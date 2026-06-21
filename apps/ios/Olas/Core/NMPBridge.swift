import Foundation
import OlasFFI
import SwiftUI
import Combine
import os

// ── Bridge ───────────────────────────────────────────────────────────────────

@Observable @MainActor final class NMPBridge {
    static let shared = NMPBridge()

    var appPtr: UnsafeMutableRawPointer?
    var isRunning = false
    var activeAccountPubkey: String?

    // Keep the sink alive as long as the bridge lives.
    private var callbackSink: NMPCallbackSink?

    // MARK: - Follow state

    // NMP-GAP(#7): followedPubkeys will be populated by a Rust follow-graph projection.
    // Until that projection exists, this set remains empty. The UI will reflect reality
    // only once Rust owns the follow state. Do NOT add optimistic mutations here.
    private(set) var followedPubkeys: Set<String> = []

    func follow(pubkey: String) {
        _ = dispatchAction(namespace: "nmp.follow", json: "{\"pubkey\":\"\(pubkey)\"}")
    }

    func unfollow(pubkey: String) {
        _ = dispatchAction(namespace: "nmp.unfollow", json: "{\"pubkey\":\"\(pubkey)\"}")
    }

    func isFollowing(_ pubkey: String) -> Bool { followedPubkeys.contains(pubkey) }

    // MARK: - Search feed (thin wrappers — filter JSON lives in Rust)

    func openSearchFeed(query: String, consumer: String) {
        guard let app = appPtr else { return }
        query.withCString { q in consumer.withCString { c in olas_open_search_feed(app, q, c) } }
    }

    func closeSearchFeed(query: String, consumer: String) {
        guard let app = appPtr else { return }
        query.withCString { q in consumer.withCString { c in olas_close_search_feed(app, q, c) } }
    }

    private init() {}

    func initialize() async {
        guard appPtr == nil else { return }
        appPtr = nmp_app_new()
        guard let app = appPtr else { return }

        olas_app_register(app)

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        docs.path(percentEncoded: false).withCString { path in
            _ = nmp_app_set_storage_path(app, path)
        }

        // ADR-0053: must declare projection-consumption intent before start.
        nmp_app_consume_all_builtin_projections(app)

        registerCallbacks(app: app)
        nmp_app_start(app, 0, 100, 4)

        // Default relay set lives in Rust — no URLs hardcoded in Swift (D3).
        olas_seed_default_relays(app)

        setupCombinePipelines()
        isRunning = true
    }

    // MARK: - Combine pipelines (replaces Task.sleep debounce — D8)

    // nonisolated(unsafe): send() is called from Rust callback on a background thread.
    // PassthroughSubject.send() uses an internal lock and is thread-safe.
    nonisolated(unsafe) private let eventPipe = PassthroughSubject<String, Never>()
    private var eventCancellable: AnyCancellable?

    private let profilePipe = PassthroughSubject<String, Never>()
    private var profileCancellable: AnyCancellable?

    private func setupCombinePipelines() {
        // Batch events: flush up to maxEventsPerFlush every 500 ms.
        eventCancellable = eventPipe
            .collect(.byTimeOrCount(DispatchQueue.main, .milliseconds(500), Self.maxEventsPerFlush))
            .sink { [weak self] batch in
                Task { @MainActor [weak self] in self?.flushEventBatch(batch) }
            }

        // Debounce profile cache updates: hundreds of profiles arrive simultaneously
        // when a relay delivers posts — coalesce into one handler call.
        profileCancellable = profilePipe
            .debounce(for: .milliseconds(1500), scheduler: DispatchQueue.main)
            .sink { [weak self] json in
                Task { @MainActor [weak self] in self?.handleClaimedProfilesJSON(json) }
            }
    }

    // MARK: - Callback registration

    // Thread-safe event entry point: called from Rust callback on background thread.
    private static let maxEventsPerFlush = 4

    nonisolated func enqueueEvent(_ json: String) {
        eventPipe.send(json)
    }

    private func flushEventBatch(_ batch: [String]) {
        for json in batch {
            recentEventBuffer.append(json)
            if recentEventBuffer.count > eventBufferCapacity {
                recentEventBuffer.removeFirst()
            }
            eventHandlers.forEach { $0(json) }
        }
    }

    // Fast-path dedup for snapshot ticks (~100ms cadence): skip the MainActor hop
    // when the JSON payload hasn't changed since the last tick.
    // nonisolated(unsafe): read/written from background Rust callbacks under tickLock.
    nonisolated(unsafe) var lastProfilesJSON = ""
    nonisolated(unsafe) var lastActiveAccountJSON = ""
    nonisolated(unsafe) let tickLock = NSLock()

    func scheduleProfileFlush(_ json: String) {
        profilePipe.send(json)
    }

    private func registerCallbacks(app: UnsafeMutableRawPointer) {
        let sink = makeBridgeCallbackSink(bridge: self)
        callbackSink = sink
        let ctx = Unmanaged.passUnretained(sink).toOpaque()
        nmp_app_register_event_observer(app, ctx, olasEventCallback)
        nmp_app_set_update_callback(app, ctx, olasUpdateCallback)
    }

    // MARK: - Event observer

    // Ring buffer of the last 500 events, matching Android's SharedFlow(replay=100) intent.
    // New handlers receive buffered events immediately so they don't miss events that
    // arrived before they registered (e.g. network-feed kind:20 already in the buffer).
    private var recentEventBuffer: [String] = []
    private let eventBufferCapacity = 500

    private var eventHandlers: [(String) -> Void] = []

    func addEventHandler(_ handler: @escaping (String) -> Void) {
        // Replay buffered events before registering so the handler sees past events.
        recentEventBuffer.forEach { handler($0) }
        eventHandlers.append(handler)
    }

    // MARK: - Profile cache (from claimed_profiles snapshot projection)

    private var profileUpdateHandlers: [([String: ProfileWire]) -> Void] = []

    func addProfileUpdateHandler(_ handler: @escaping ([String: ProfileWire]) -> Void) {
        profileUpdateHandlers.append(handler)
    }

    func handleActiveAccountJSON(_ json: String) {
        os.Logger(subsystem: "io.f7z.olas", category: "feeddiag").error("FEEDDIAG activeAccountJSON raw=\(json) current=\(self.activeAccountPubkey ?? "nil")")
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: String],
              let pubkey = obj["pubkey"], !pubkey.isEmpty else { return }
        if activeAccountPubkey != pubkey {
            os.Logger(subsystem: "io.f7z.olas", category: "feeddiag").error("FEEDDIAG activeAccountPubkey CHANGED \(self.activeAccountPubkey ?? "nil") -> \(pubkey)")
            activeAccountPubkey = pubkey
        }
    }

    private func handleClaimedProfilesJSON(_ json: String) {
        guard let data = json.data(using: .utf8),
              let rows = try? JSONDecoder().decode([[String: String?]].self, from: data) else { return }
        var changed = false
        for row in rows {
            guard let pubkey = row["pubkey"] as? String, !pubkey.isEmpty else { continue }
            let displayName = row["display_name"] as? String
            let about = row["about"] as? String
            let pictureUrl = row["picture_url"] as? String
            let nip05 = row["nip05"] as? String
            let npub = row["npub"] as? String ?? ""
            let npubShort = row["npub_short"] as? String ?? String(npub.prefix(12))
            let cached = profileCache[pubkey]
            let wire = ProfileWire(
                pubkey: pubkey,
                displayName: displayName ?? cached?.displayName,
                about: about ?? cached?.about,
                pictureUrl: pictureUrl ?? cached?.pictureUrl,
                nip05: nip05 ?? cached?.nip05,
                npub: npub.isEmpty ? (cached?.npub ?? "") : npub,
                npubShort: npubShort.isEmpty ? (cached?.npubShort ?? "") : npubShort
            )
            if cached != wire { profileCache[pubkey] = wire; changed = true }
        }
        if changed { profileUpdateHandlers.forEach { $0(profileCache) } }
    }

    // MARK: - Action results

    struct ActionTerminal {
        let status: String
        let resultJSON: String
        var succeeded: Bool { status != "failed" }
    }

    private var actionResultWaiters: [String: CheckedContinuation<ActionTerminal?, Never>] = [:]

    func handleActionResultsJSON(_ json: String) {
        guard !actionResultWaiters.isEmpty,
              let data = json.data(using: .utf8),
              let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return }
        for row in rows {
            guard let cid = row["correlation_id"] as? String,
                  let waiter = actionResultWaiters.removeValue(forKey: cid) else { continue }
            let status = (row["status"] as? String) ?? "published"
            let resultJSON: String
            if let result = row["result"], !(result is NSNull),
               let resultData = try? JSONSerialization.data(withJSONObject: result),
               let resultStr = String(data: resultData, encoding: .utf8) {
                resultJSON = resultStr
            } else {
                resultJSON = "null"
            }
            waiter.resume(returning: ActionTerminal(status: status, resultJSON: resultJSON))
        }
    }

    // NMP-GAP(#18): dispatchAndAwaitResult suspends on an action terminal, making native
    // Swift the owner of the upload/publish lifecycle state machine. Once NMP ships a
    // stream-based action-result projection, this bridging shim must be removed.
    func dispatchAndAwaitResult(namespace: String, json: String, timeoutSeconds: Double = 30) async -> ActionTerminal? {
        guard let returnJSON = dispatchAction(namespace: namespace, json: json),
              let data = returnJSON.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let cid = obj["correlation_id"] as? String else { return nil }
        return await withCheckedContinuation { [weak self] continuation in
            guard let self else { continuation.resume(returning: nil); return }
            self.actionResultWaiters[cid] = continuation
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(timeoutSeconds * 1_000_000_000))
                // Already on @MainActor (Task inherits actor from creation context).
                if let waiter = self?.actionResultWaiters.removeValue(forKey: cid) {
                    waiter.resume(returning: ActionTerminal(status: "published", resultJSON: "null"))
                }
            }
        }
    }

    // MARK: - Identity

    func createAccount(name: String, username: String) {
        guard let app = appPtr else { return }
        name.withCString { n in username.withCString { u in olas_create_account(app, n, u) } }
    }

    func signInNsec(_ nsec: String) {
        guard let app = appPtr else { return }
        nsec.withCString { nmp_app_signin_nsec(app, $0, 1) }
    }

    func signInBunker(_ uri: String) {
        guard let app = appPtr else { return }
        uri.withCString { nmp_app_signin_bunker(app, $0, 1) }
    }

    /// Sign out of the active account: remove its signer from the kernel and
    /// clear local active-account state so the UI reverts to the sign-in prompt.
    /// `identity_id` is the active account's hex pubkey (nmp-core keys accounts
    /// by pubkey hex). A removed active account produces no `active_account`
    /// snapshot frame, so nothing clears `activeAccountPubkey` for us — do it here.
    func signOut() {
        guard let app = appPtr, let pubkey = activeAccountPubkey, !pubkey.isEmpty else { return }
        pubkey.withCString { nmp_app_remove_account(app, $0) }
        activeAccountPubkey = nil
    }

    // MARK: - Feed

    func openAuthorPhotoFeed(pubkey: String) {
        guard let app = appPtr else { return }
        let consumer = "olas.author.\(pubkey)"
        pubkey.withCString { pk in consumer.withCString { cid in
            olas_open_author_photo_feed(app, pk, cid)
        }}
    }

    func closeAuthorPhotoFeed(pubkey: String) {
        guard let app = appPtr else { return }
        let consumer = "olas.author.\(pubkey)"
        pubkey.withCString { pk in consumer.withCString { cid in
            olas_close_author_photo_feed(app, pk, cid)
        }}
    }

    // MARK: - Profile

    // NMP-GAP(#5): profileCache is a read-only view populated from the claimed_profiles
    // Rust projection. It is NOT an authoritative store — only Rust's snapshot is truth.
    // A future typed projection will supersede this dictionary entirely.
    private(set) var profileCache: [String: ProfileWire] = [:]

    // MARK: - Event decoders

    func decodeKind20Event(_ json: String) -> String? {
        json.withCString { ptr in
            guard let res = olas_decode_kind20_event_json(ptr) else { return nil }
            defer { nmp_free_string(res) }
            return String(cString: res)
        }
    }

    func decodeKind0Event(_ json: String) -> String? {
        json.withCString { ptr in
            guard let res = olas_decode_kind0_event_json(ptr) else { return nil }
            defer { nmp_free_string(res) }
            return String(cString: res)
        }
    }

    func decodeZapNotification(_ json: String) -> String? {
        json.withCString { ptr in
            guard let res = olas_decode_zap_notification_json(ptr) else { return nil }
            defer { nmp_free_string(res) }
            return String(cString: res)
        }
    }

    func bolt11AmountMsats(_ bolt11: String) -> Int64 {
        bolt11.withCString { olas_bolt11_amount_msats($0) }
    }

    func computeGeohash(lat: Double, lon: Double, precision: Int32 = 6) -> String? {
        guard let res = olas_compute_geohash(lat, lon, precision) else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func buildZapActionJSON(eventId: String, sats: Int64) -> String? {
        eventId.withCString { id in
            guard let res = olas_build_zap_action_json(id, sats) else { return nil }
            defer { nmp_free_string(res) }
            return String(cString: res)
        }
    }

    func filterCatalogJSON() -> String? {
        guard let res = olas_filter_catalog_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func mediaUploadConfigJSON() -> String? {
        guard let res = olas_media_upload_config_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func pickerConfigJSON() -> String? {
        guard let res = olas_picker_config_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func settingsCatalogJSON() -> String? {
        guard let res = olas_settings_catalog_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func onboardingStepsJSON() -> String? {
        guard let res = olas_onboarding_steps_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func composeStepsJSON() -> String? {
        guard let res = olas_compose_steps_json() else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    var blossomServerURL: String {
        guard let res = olas_blossom_server_url_get(appPtr) else { return "" }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    func setBlossomServerURL(_ url: String) {
        guard let app = appPtr else { return }
        url.withCString { olas_blossom_server_url_set(app, $0) }
    }

    private(set) var feedMode: String = "network"

    func setFeedMode(_ mode: String) {
        guard let app = appPtr else { return }
        mode.withCString { olas_feed_mode_set(app, $0) }
        feedMode = mode
    }

    // MARK: - P0-E: Real social proof

    /// Query social proof for `targetPubkey` from `activePubkey`'s follow graph.
    /// Returns: `{"mutual_followers":[...],"mutual_count":N,"reason_kind":"followed_by_mutuals"|"new_account"}`
    /// Returns nil when the active account is unknown or the WoT graph is not yet bootstrapped.
    func socialProofJSON(activePubkey: String, targetPubkey: String) -> String? {
        guard let app = appPtr, !activePubkey.isEmpty, !targetPubkey.isEmpty else { return nil }
        return activePubkey.withCString { ap in
            targetPubkey.withCString { tp in
                guard let res = olas_social_proof_json(app, ap, tp) else { return nil }
                defer { nmp_free_string(res) }
                return String(cString: res)
            }
        }
    }

    // MARK: - P0-F: Ranked discover sections

    /// Return ranked discover sections for `activePubkey` from the WoT follow graph.
    /// Returns: `[{"title":"...","reason":"...","profiles":[{"pubkey":"...","mutual_count":N}]}]`
    /// Returns nil when the active account is unknown or the WoT runtime is absent.
    func discoverSectionsJSON(activePubkey: String) -> String? {
        guard let app = appPtr, !activePubkey.isEmpty else { return nil }
        return activePubkey.withCString { ap in
            guard let res = olas_discover_sections_json(app, ap) else { return nil }
            defer { nmp_free_string(res) }
            return String(cString: res)
        }
    }

    // MARK: - P2-A: Invite link resolution

    /// Decode an invite token (full URL, bare npub, or hex pubkey) into inviter info.
    /// Returns `(pubkey: "<hex>", hint: "npub1...")` or nil.
    func resolveInvite(token: String) -> (pubkey: String, hint: String)? {
        token.withCString { t in
            guard let res = olas_resolve_invite_json(t) else { return nil }
            defer { nmp_free_string(res) }
            guard let data = String(cString: res).data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: String],
                  let pk = obj["inviter_pubkey"],
                  let hint = obj["display_hint"]
            else { return nil }
            return (pubkey: pk, hint: hint)
        }
    }

    // MARK: - P2-C: Invite link minting

    /// Mint the canonical invite link for the active user.
    /// Returns "https://olas.app/i/<npub>" or nil when no account is active.
    func myInviteLink() -> String? {
        guard let pk = activeAccountPubkey, !pk.isEmpty else { return nil }
        return pk.withCString { c in
            guard let res = olas_my_invite_link(c) else { return nil }
            defer { nmp_free_string(res) }
            return String(cString: res)
        }
    }

}

// MARK: - NostrProfileHost

extension NMPBridge: NostrProfileHost {
    func profile(forPubkey pubkey: String) -> ProfileWire? { profileCache[pubkey] }
    func claimProfile(pubkey: String, consumerID: String) { claimProfile(pubkey: pubkey, consumer: consumerID) }
    func releaseProfile(pubkey: String, consumerID: String) { releaseProfile(pubkey: pubkey, consumer: consumerID) }
}
