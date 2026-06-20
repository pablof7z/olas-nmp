import Foundation
import OlasFFI

extension NMPBridge {
    // MARK: - Feed

    func openFollowingFeed() {
        guard let app = appPtr else { return }
        "olas.following_feed".withCString { olas_open_photo_feed(app, 1, $0) }
    }

    func openNetworkFeed() {
        guard let app = appPtr else { return }
        "olas.network_feed".withCString { olas_open_photo_feed(app, 0, $0) }
    }

    func photoPost(from eventJSON: String, mode: FeedMode) -> PhotoPost? {
        let contactListOnly: UInt8 = mode == .following ? 1 : 0
        let preset = UserDefaults.standard.string(forKey: "wotPreset") ?? "balanced"
        let ptr = eventJSON.withCString { eventPtr in
            preset.withCString { presetPtr in
                olas_filter_photo_post_json(eventPtr, contactListOnly, presetPtr)
            }
        }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        let json = String(cString: ptr)
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(PhotoPost.self, from: data)
    }

    func profile(from eventJSON: String) -> OlasProfile? {
        let ptr = eventJSON.withCString { olas_profile_json($0) }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        let json = String(cString: ptr)
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(OlasProfile.self, from: data)
    }

    func notification(from eventJSON: String) -> OlasNotification? {
        let ptr = eventJSON.withCString { olas_notification_json($0) }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        let json = String(cString: ptr)
        guard let data = json.data(using: .utf8),
              let payload = try? JSONDecoder().decode(OlasNotificationPayload.self, from: data)
        else { return nil }
        return payload.toNotification()
    }

    func contactListPubkeys(from eventJSON: String, activePubkey: String) -> [String]? {
        let ptr = eventJSON.withCString { eventPtr in
            activePubkey.withCString { activePtr in
                olas_contact_list_pubkeys_json(eventPtr, activePtr)
            }
        }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        let json = String(cString: ptr)
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode([String].self, from: data)
    }

    func defaultRelays() -> [RelayEntry] {
        guard let ptr = olas_default_relays_json() else { return [] }
        defer { nmp_free_string(ptr) }
        let json = String(cString: ptr)
        guard let data = json.data(using: .utf8),
              let relays = try? JSONDecoder().decode([DefaultRelayPayload].self, from: data)
        else { return [] }
        return relays.map {
            RelayEntry(
                id: $0.id,
                url: $0.url,
                role: $0.role,
                isConnected: $0.connected,
                latencyMs: nil
            )
        }
    }

    func loadOlderFeed(key: String) {
        guard let app = appPtr else { return }
        key.withCString { nmp_app_load_older_feed(app, $0) }
    }

    // MARK: - Profile

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

    func react(to post: PhotoPost) -> String? {
        post.id.withCString { eventId in
            post.authorPubkey.withCString { author in
                guard let ptr = olas_react_action_json(eventId, author) else { return nil }
                defer { nmp_free_string(ptr) }
                let json = String(cString: ptr)
                return dispatchAction(namespace: "nmp.nip25.react", json: json)
            }
        }
    }

    func bookmark(post: PhotoPost, add: Bool) -> String? {
        guard let account = activeAccountPubkey else { return nil }
        return account.withCString { accountPubkey in
            post.id.withCString { eventId in
                guard let ptr = olas_bookmark_event_action_json(accountPubkey, eventId) else { return nil }
                defer { nmp_free_string(ptr) }
                let json = String(cString: ptr)
                let namespace = add ? "nmp.nip51.add_bookmark" : "nmp.nip51.remove_bookmark"
                return dispatchAction(namespace: namespace, json: json)
            }
        }
    }

    func zap(post: PhotoPost, amountSats: Int64, comment: String?) -> String? {
        let amountMsats = UInt64(max(0, amountSats) * 1000)
        func withOpt(_ s: String?, _ body: (UnsafePointer<CChar>?) -> String?) -> String? {
            if let s, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return s.withCString { body($0) }
            }
            return body(nil)
        }
        return post.authorPubkey.withCString { recipient in
            post.id.withCString { eventId in
                withOpt(comment) { commentPtr in
                    guard let ptr = olas_zap_action_json(recipient, eventId, amountMsats, commentPtr) else { return nil }
                    defer { nmp_free_string(ptr) }
                    let json = String(cString: ptr)
                    return dispatchAction(namespace: "nmp.nip57.zap", json: json)
                }
            }
        }
    }

    func bolt11AmountSats(_ bolt11: String) -> Int64? {
        let amount = bolt11.withCString { olas_bolt11_amount_sats($0) }
        guard amount > 0, amount <= UInt64(Int64.max) else { return nil }
        return Int64(amount)
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
                withOpt(alt) { a in
                    withOpt(dim) { d in
                        withOpt(geohash) { g in
                            guard let ptr = olas_picture_post_publish_json(r, c, a, d, g) else { return nil }
                            defer { nmp_free_string(ptr) }
                            return String(cString: ptr)
                        }
                    }
                }
            }
        }
    }

    func locationGeohash4(latitude: Double, longitude: Double) -> String? {
        guard let ptr = olas_location_geohash4(latitude, longitude) else { return nil }
        defer { nmp_free_string(ptr) }
        return String(cString: ptr)
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

private struct OlasNotificationPayload: Codable {
    let id: String
    let kind: String
    let actorPubkey: String
    let postId: String?
    let createdAt: Int64
    let zapSats: Int64?

    func toNotification() -> OlasNotification? {
        let type: OlasNotification.NotificationType
        switch kind {
        case "reaction": type = .reaction
        case "zap": type = .zap(zapSats ?? 0)
        case "comment": type = .comment
        case "follow": type = .follow
        default: return nil
        }
        return OlasNotification(
            id: id,
            type: type,
            actorPubkey: actorPubkey,
            actorName: nil,
            actorAvatar: nil,
            postId: postId,
            postThumbnail: nil,
            createdAt: createdAt
        )
    }
}

private struct DefaultRelayPayload: Codable {
    let id: String
    let url: String
    let role: String
    let connected: Bool
}
