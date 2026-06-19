import Combine
import Foundation
import OlasFFI

@Observable @MainActor
final class NMPBridge {
    static let shared = NMPBridge()

    private(set) var appPtr: UnsafeMutableRawPointer?
    var isRunning = false
    var activeAccountPubkey: String?
    private(set) var profileCache: [String: (name: String, avatar: String?)] = [:]
    private(set) var followedPubkeys: Set<String> = []

    private var callbackSink: NMPCallbackSink?
    private var runningCallbacks: [() -> Void] = []
    private var eventHandlers: [(String) -> Void] = []
    private var profileUpdateHandlers: [([String: (name: String, avatar: String?)]) -> Void] = []
    private var actionResultWaiters: [String: CheckedContinuation<ActionTerminal?, Never>] = [:]

    @ObservationIgnored nonisolated(unsafe) private let eventPipe = PassthroughSubject<String, Never>()
    @ObservationIgnored private var eventCancellable: AnyCancellable?
    @ObservationIgnored private let profilePipe = PassthroughSubject<String, Never>()
    @ObservationIgnored private var profileCancellable: AnyCancellable?

    @ObservationIgnored nonisolated(unsafe) var lastProfilesJSON = ""
    @ObservationIgnored nonisolated(unsafe) var lastActiveAccountJSON = ""
    @ObservationIgnored let tickLock = NSLock()

    private static let maxEventsPerFlush = 4

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

        nmp_app_consume_all_builtin_projections(app)
        setupPipelines()
        registerCallbacks(app: app)
        nmp_app_start(app, 0, 100, 4)

        isRunning = true
        let callbacks = runningCallbacks
        runningCallbacks.removeAll()
        callbacks.forEach { $0() }
    }

    func whenRunning(_ callback: @escaping () -> Void) {
        if isRunning {
            callback()
        } else {
            runningCallbacks.append(callback)
        }
    }

    func createAccount(name: String, username: String) {
        guard let app = appPtr else { return }
        name.withCString { namePtr in
            username.withCString { usernamePtr in
                olas_create_account(app, namePtr, usernamePtr)
            }
        }
    }

    func signInNsec(_ nsec: String) {
        guard let app = appPtr else { return }
        nsec.withCString { nmp_app_signin_nsec(app, $0, 1) }
    }

    func signInBunker(_ uri: String) {
        guard let app = appPtr else { return }
        uri.withCString { nmp_app_signin_bunker(app, $0, 1) }
    }

    func follow(pubkey: String) {
        _ = dispatchAction(namespace: "nmp.follow", json: "{\"pubkey\":\"\(pubkey)\"}")
    }

    func unfollow(pubkey: String) {
        _ = dispatchAction(namespace: "nmp.unfollow", json: "{\"pubkey\":\"\(pubkey)\"}")
    }

    func isFollowing(_ pubkey: String) -> Bool {
        followedPubkeys.contains(pubkey)
    }

    func openSearchFeed(query: String, consumer: String) {
        guard let app = appPtr else { return }
        query.withCString { queryPtr in
            consumer.withCString { consumerPtr in
                olas_open_search_feed(app, queryPtr, consumerPtr)
            }
        }
    }

    func closeSearchFeed(query: String, consumer: String) {
        guard let app = appPtr else { return }
        query.withCString { queryPtr in
            consumer.withCString { consumerPtr in
                olas_close_search_feed(app, queryPtr, consumerPtr)
            }
        }
    }

    nonisolated func enqueueEvent(_ json: String) {
        eventPipe.send(json)
    }

    func scheduleProfileFlush(_ json: String) {
        profilePipe.send(json)
    }

    private func setupPipelines() {
        eventCancellable = eventPipe
            .collect(.byTimeOrCount(DispatchQueue.main, .milliseconds(500), Self.maxEventsPerFlush))
            .sink { [weak self] batch in
                Task { @MainActor [weak self] in
                    self?.flushEventBatch(batch)
                }
            }

        profileCancellable = profilePipe
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .sink { [weak self] json in
                Task { @MainActor [weak self] in
                    self?.handleClaimedProfilesJSON(json)
                }
            }
    }

    private func registerCallbacks(app: UnsafeMutableRawPointer) {
        let sink = makeBridgeCallbackSink(bridge: self)
        callbackSink = sink
        let ctx = Unmanaged.passUnretained(sink).toOpaque()
        nmp_app_register_event_observer(app, ctx, olasEventCallback)
        nmp_app_set_update_callback(app, ctx, olasUpdateCallback)
    }

    private func flushEventBatch(_ batch: [String]) {
        for json in batch {
            if let active = activeAccountPubkey,
               let pubkeys = contactListPubkeys(from: json, activePubkey: active) {
                followedPubkeys = Set(pubkeys)
            }
            eventHandlers.forEach { $0(json) }
        }
    }

    func addEventHandler(_ handler: @escaping (String) -> Void) {
        eventHandlers.append(handler)
    }

    func addProfileUpdateHandler(_ handler: @escaping ([String: (name: String, avatar: String?)]) -> Void) {
        profileUpdateHandlers.append(handler)
    }

    func handleActiveAccountJSON(_ json: String) {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: String],
              let pubkey = obj["pubkey"],
              !pubkey.isEmpty else { return }
        if activeAccountPubkey != pubkey {
            activeAccountPubkey = pubkey
        }
    }

    private func handleClaimedProfilesJSON(_ json: String) {
        guard let data = json.data(using: .utf8),
              let rows = try? JSONDecoder().decode([[String: String?]].self, from: data)
        else { return }

        var changed = false
        for row in rows {
            guard let pubkey = row["pubkey"] ?? nil, !pubkey.isEmpty else { continue }
            let displayName = row["display_name"] ?? nil
            let picture = row["picture_url"] ?? nil
            let cached = profileCache[pubkey]
            let name = displayName ?? cached?.name ?? ""
            let avatar = picture ?? cached?.avatar

            if cached?.name != name || cached?.avatar != avatar {
                profileCache[pubkey] = (name: name, avatar: avatar)
                changed = true
            }
        }

        if changed {
            profileUpdateHandlers.forEach { $0(profileCache) }
        }
    }

    struct ActionTerminal {
        let status: String
        let resultJSON: String
        var succeeded: Bool { status != "failed" }
    }

    func handleActionResultsJSON(_ json: String) {
        guard !actionResultWaiters.isEmpty,
              let data = json.data(using: .utf8),
              let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return }

        for row in rows {
            guard let cid = row["correlation_id"] as? String,
                  let waiter = actionResultWaiters.removeValue(forKey: cid)
            else { continue }

            let status = row["status"] as? String ?? "published"
            let resultJSON: String
            if let result = row["result"],
               !(result is NSNull),
               let resultData = try? JSONSerialization.data(withJSONObject: result),
               let resultString = String(data: resultData, encoding: .utf8) {
                resultJSON = resultString
            } else {
                resultJSON = "null"
            }
            waiter.resume(returning: ActionTerminal(status: status, resultJSON: resultJSON))
        }
    }

    func dispatchAndAwaitResult(namespace: String, json: String) async -> ActionTerminal? {
        guard let returnJSON = dispatchAction(namespace: namespace, json: json),
              let data = returnJSON.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let cid = obj["correlation_id"] as? String
        else { return nil }

        return await withCheckedContinuation { continuation in
            actionResultWaiters[cid] = continuation
        }
    }
}
