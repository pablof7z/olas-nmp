import Foundation
import OlasFFI
import SwiftUI
import Combine

// ── Bridge ───────────────────────────────────────────────────────────────────

@Observable @MainActor final class NMPBridge {
    static let shared = NMPBridge()

    private var appPtr: UnsafeMutableRawPointer?
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
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: String],
              let pubkey = obj["pubkey"], !pubkey.isEmpty else { return }
        if activeAccountPubkey != pubkey { activeAccountPubkey = pubkey }
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
                await MainActor.run { [weak self] in
                    // If still pending (relay never ACKed), resolve optimistically as published.
                    if let waiter = self?.actionResultWaiters.removeValue(forKey: cid) {
                        waiter.resume(returning: ActionTerminal(status: "published", resultJSON: "null"))
                    }
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

    // MARK: - Feed

    func openFollowingFeed() {
        guard let app = appPtr else { return }
        "olas.following_feed".withCString { olas_open_photo_feed(app, 1, $0) }
    }

    func openNetworkFeed() {
        guard let app = appPtr else { return }
        "olas.network_feed".withCString { olas_open_photo_feed(app, 0, $0) }
    }

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

    func loadOlderFeed(key: String) {
        guard let app = appPtr else { return }
        key.withCString { nmp_app_load_older_feed(app, $0) }
    }

    // MARK: - Profile

    // NMP-GAP(#5): profileCache is a read-only view populated from the claimed_profiles
    // Rust projection. It is NOT an authoritative store — only Rust's snapshot is truth.
    // A future typed projection will supersede this dictionary entirely.
    private(set) var profileCache: [String: ProfileWire] = [:]

    func claimProfile(pubkey: String, consumer: String = "olas.profile") {
        guard let app = appPtr else { return }
        pubkey.withCString { pk in consumer.withCString { c in nmp_app_claim_profile(app, pk, c, 1) } }
    }

    func releaseProfile(pubkey: String, consumer: String = "olas.profile") {
        guard let app = appPtr else { return }
        pubkey.withCString { pk in consumer.withCString { c in nmp_app_release_profile(app, pk, c) } }
    }

    // MARK: - Actions

    func dispatchAction(namespace: String, json: String) -> String? {
        guard let app = appPtr else { return nil }
        return namespace.withCString { ns in
            json.withCString { j in
                guard let ptr = nmp_app_dispatch_action(app, ns, j) else { return nil }
                defer { nmp_free_string(ptr) }
                return String(cString: ptr)
            }
        }
    }

    func blossomUploadInputJSON(filePath: String, mime: String, serverURL: String) -> String? {
        filePath.withCString { fp in
            mime.withCString { m in
                serverURL.withCString { s in
                    guard let ptr = olas_blossom_upload_input_json(fp, m, s) else { return nil }
                    defer { nmp_free_string(ptr) }
                    return String(cString: ptr)
                }
            }
        }
    }

    func picturePostPublishJSON(blossomResultJSON: String, caption: String, alt: String?, dim: String?, geohash: String?) -> String? {
        func withOpt(_ s: String?, _ body: (UnsafePointer<CChar>?) -> String?) -> String? {
            if let s { return s.withCString { body($0) } }
            return body(nil)
        }
        return blossomResultJSON.withCString { r in
            caption.withCString { c in
                withOpt(alt) { a in withOpt(dim) { d in withOpt(geohash) { g in
                    guard let ptr = olas_picture_post_publish_json(r, c, a, d, g) else { return nil }
                    defer { nmp_free_string(ptr) }
                    return String(cString: ptr)
                }}}
            }
        }
    }

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

    // MARK: - Relay management

    func addRelay(url: String, role: String) {
        guard let app = appPtr else { return }
        url.withCString { u in role.withCString { r in nmp_app_add_relay(app, u, r) } }
    }

    func removeRelay(url: String) {
        guard let app = appPtr else { return }
        url.withCString { nmp_app_remove_relay(app, $0) }
    }

    // MARK: - Wallet

    func connectWallet(uri: String) {
        // NMP-GAP: nmp_app_wallet_connect was removed from the FFI surface;
        // dispatch via the action bus until the NMP wallet projection lands.
        let escaped = uri.replacingOccurrences(of: "\"", with: "\\\"")
        _ = dispatchAction(namespace: "nmp.wallet_connect", json: "{\"uri\":\"\(escaped)\"}")
    }

    // MARK: - Lifecycle

    func appDidBecomeActive() {
        guard let app = appPtr else { return }
        nmp_app_lifecycle_foreground(app)
    }

    func appDidEnterBackground() {
        guard let app = appPtr else { return }
        nmp_app_lifecycle_background(app)
    }
}

// MARK: - NostrProfileHost

extension NMPBridge: NostrProfileHost {
    func profile(forPubkey pubkey: String) -> ProfileWire? { profileCache[pubkey] }
    func claimProfile(pubkey: String, consumerID: String) { claimProfile(pubkey: pubkey, consumer: consumerID) }
    func releaseProfile(pubkey: String, consumerID: String) { releaseProfile(pubkey: pubkey, consumer: consumerID) }
}
