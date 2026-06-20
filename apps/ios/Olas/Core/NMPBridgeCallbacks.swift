import Foundation
import OlasFFI

// ── File-level C callbacks (non-isolated) ───────────────────────────────────
// Must live at module scope, NOT inside a @MainActor method — Swift 6 injects
// actor-isolation checks that crash when Rust calls from a background thread.

final class NMPCallbackSink {
    let onEvent: (String) -> Void
    let onUpdate: (String) -> Void
    let onProfiles: (String) -> Void
    let onActiveAccount: (String) -> Void
    let onPhotoFeed: (String, String) -> Void
    init(
        onEvent: @escaping (String) -> Void,
        onUpdate: @escaping (String) -> Void,
        onProfiles: @escaping (String) -> Void,
        onActiveAccount: @escaping (String) -> Void,
        onPhotoFeed: @escaping (String, String) -> Void
    ) {
        self.onEvent = onEvent
        self.onUpdate = onUpdate
        self.onProfiles = onProfiles
        self.onActiveAccount = onActiveAccount
        self.onPhotoFeed = onPhotoFeed
    }
}

private let olasPhotoFeedKeys = ["olas.following_feed", "olas.network_feed"]

// File-scope factory so closures do NOT inherit @MainActor from callers.
// Swift 6 would infer closures inside a @MainActor method as @MainActor-isolated,
// causing dispatch_assert_queue_fail when Rust invokes them from a background thread.
func makeBridgeCallbackSink(bridge: NMPBridge) -> NMPCallbackSink {
    NMPCallbackSink(
        onEvent: { [weak bridge] json in bridge?.enqueueEvent(json) },
        onUpdate: { [weak bridge] json in
            Task { @MainActor [weak bridge] in bridge?.handleActionResultsJSON(json) }
        },
        onProfiles: { [weak bridge] json in
            guard let bridge else { return }
            let changed = bridge.tickLock.withLock {
                if json == bridge.lastProfilesJSON { return false }
                bridge.lastProfilesJSON = json
                return true
            }
            guard changed else { return }
            Task { @MainActor [weak bridge] in bridge?.scheduleProfileFlush(json) }
        },
        onActiveAccount: { [weak bridge] json in
            guard let bridge else { return }
            let changed = bridge.tickLock.withLock {
                if json == bridge.lastActiveAccountJSON { return false }
                bridge.lastActiveAccountJSON = json
                return true
            }
            guard changed else { return }
            Task { @MainActor [weak bridge] in bridge?.handleActiveAccountJSON(json) }
        },
        onPhotoFeed: { [weak bridge] key, json in
            guard let bridge else { return }
            let changed = bridge.tickLock.withLock {
                if bridge.lastPhotoFeedJSON[key] == json { return false }
                bridge.lastPhotoFeedJSON[key] = json
                return true
            }
            guard changed else { return }
            Task { @MainActor [weak bridge] in bridge?.handlePhotoFeedJSON(key: key, json: json) }
        }
    )
}

let olasEventCallback: NmpEventObserverCallback = { context, eventJson in
    guard let context, let eventJson else { return }
    let sink = Unmanaged<NMPCallbackSink>.fromOpaque(context).takeUnretainedValue()
    guard let json = String(validatingCString: eventJson) else { return }
    sink.onEvent(json)
}

let olasUpdateCallback: NmpUpdateCallback = { context, data, len in
    guard let context, let data, len > 0 else { return }
    let sink = Unmanaged<NMPCallbackSink>.fromOpaque(context).takeUnretainedValue()
    if let ptr = olas_decode_snapshot_action_results_json(data, len) {
        if let json = String(validatingCString: ptr) { sink.onUpdate(json) }
        nmp_free_string(ptr)
    }
    if let ptr = olas_decode_snapshot_claimed_profiles_json(data, len) {
        if let json = String(validatingCString: ptr) { sink.onProfiles(json) }
        nmp_free_string(ptr)
    }
    if let ptr = olas_decode_snapshot_active_account_json(data, len) {
        if let json = String(validatingCString: ptr) { sink.onActiveAccount(json) }
        nmp_free_string(ptr)
    }
    for key in olasPhotoFeedKeys {
        key.withCString { keyPtr in
            if let ptr = olas_decode_snapshot_photo_feed_json(data, len, keyPtr) {
                if let json = String(validatingCString: ptr) { sink.onPhotoFeed(key, json) }
                nmp_free_string(ptr)
            }
        }
    }
}
