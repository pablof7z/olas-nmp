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

    // MARK: - P0-B: multi-image publish

    /// Build the nmp.publish (PublishRaw) kind:20 input JSON from an array of
    /// finished Blossom upload results. Each element carries the BUD-02
    /// descriptor plus per-image alt text and pixel dimensions.
    ///
    /// uploadedImages: array of (descriptor JSON string, alt, dim "WxH") tuples.
    /// caption: kind:20 content string.
    /// geohash: optional 4-char precision geohash for the "g" tag.
    func picturePostPublishJSON(
        uploadedImages: [(descriptorJSON: String, alt: String?, dim: String?)],
        caption: String,
        geohash: String?
    ) -> String? {
        // Build the JSON array that Rust expects.
        var entries: [[String: Any]] = []
        for item in uploadedImages {
            guard let descriptorData = item.descriptorJSON.data(using: .utf8),
                  let descriptorObj = try? JSONSerialization.jsonObject(with: descriptorData)
            else { continue }
            var entry: [String: Any] = ["descriptor": descriptorObj]
            if let alt = item.alt, !alt.isEmpty { entry["alt"] = alt }
            if let dim = item.dim, !dim.isEmpty { entry["dim"] = dim }
            entries.append(entry)
        }
        guard !entries.isEmpty,
              let entriesData = try? JSONSerialization.data(withJSONObject: entries),
              let entriesJSON = String(data: entriesData, encoding: .utf8)
        else { return nil }

        func withOpt(_ s: String?, _ body: (UnsafePointer<CChar>?) -> String?) -> String? {
            if let s { return s.withCString { body($0) } }
            return body(nil)
        }
        return entriesJSON.withCString { imagesPtr in
            caption.withCString { captionPtr in
                withOpt(geohash) { geoPtr in
                    guard let ptr = olas_picture_post_publish_json(imagesPtr, captionPtr, geoPtr) else { return nil }
                    defer { nmp_free_string(ptr) }
                    return String(cString: ptr)
                }
            }
        }
    }

    // MARK: - P3-B: notification grouping

    /// Return the raw JSON payload string produced by `olas_notification_json`
    /// for a given raw KernelEvent JSON. Used to accumulate payloads before
    /// passing them to `groupNotificationsJSON`.
    func notificationPayloadJSON(_ eventJSON: String) -> String? {
        let ptr = eventJSON.withCString { olas_notification_json($0) }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        return String(cString: ptr)
    }

    /// Group an array of raw notification payload JSON strings into clustered
    /// rows (one per kind+target_post pair). `payloadsArrayJSON` is a valid
    /// JSON array of individual notification payload objects.
    func groupNotificationsJSON(_ payloadsArrayJSON: String) -> [OlasGroupedNotification]? {
        payloadsArrayJSON.withCString { ptr in
            guard let res = olas_group_notifications_json(ptr) else { return nil }
            defer { nmp_free_string(res) }
            let json = String(cString: res)
            guard let data = json.data(using: .utf8) else { return nil }
            return try? JSONDecoder().decode([OlasGroupedNotification].self, from: data)
        }
    }

    // MARK: - P3-C: caption tag parsing

    /// Parse `nostr:npub1…` mentions (NIP-27) and `#hashtag` tokens from a caption.
    /// Returns nil when the caption contains no mentions or hashtags (or is empty).
    func parseCaptionTagsJSON(_ caption: String) -> CaptionTagsPayload? {
        caption.withCString { ptr in
            guard let res = olas_parse_caption_tags_json(ptr) else { return nil }
            defer { nmp_free_string(res) }
            let json = String(cString: res)
            guard let data = json.data(using: .utf8) else { return nil }
            return try? JSONDecoder().decode(CaptionTagsPayload.self, from: data)
        }
    }

    /// Extended picture-post publish that injects `p` and `t` tags extracted
    /// from the caption. `extraTagsJSON` is a JSON array of tag arrays, e.g.
    /// `[["p","<hex>"],["t","bitcoin"]]`. Pass nil to get the same result as
    /// `picturePostPublishJSON`.
    func picturePostPublishTaggedJSON(
        uploadedImages: [(descriptorJSON: String, alt: String?, dim: String?)],
        caption: String,
        geohash: String?,
        extraTagsJSON: String?
    ) -> String? {
        var entries: [[String: Any]] = []
        for item in uploadedImages {
            guard let descriptorData = item.descriptorJSON.data(using: .utf8),
                  let descriptorObj = try? JSONSerialization.jsonObject(with: descriptorData)
            else { continue }
            var entry: [String: Any] = ["descriptor": descriptorObj]
            if let alt = item.alt, !alt.isEmpty { entry["alt"] = alt }
            if let dim = item.dim, !dim.isEmpty { entry["dim"] = dim }
            entries.append(entry)
        }
        guard !entries.isEmpty,
              let entriesData = try? JSONSerialization.data(withJSONObject: entries),
              let entriesJSON = String(data: entriesData, encoding: .utf8)
        else { return nil }

        func withOpt(_ s: String?, _ body: (UnsafePointer<CChar>?) -> String?) -> String? {
            if let s { return s.withCString { body($0) } }
            return body(nil)
        }
        return entriesJSON.withCString { imagesPtr in
            caption.withCString { captionPtr in
                withOpt(geohash) { geoPtr in
                    withOpt(extraTagsJSON) { extraPtr in
                        guard let ptr = olas_picture_post_publish_tagged_json(
                            imagesPtr, captionPtr, geoPtr, extraPtr
                        ) else { return nil }
                        defer { nmp_free_string(ptr) }
                        return String(cString: ptr)
                    }
                }
            }
        }
    }

    // MARK: - P3-D: recovery key export

    /// Return the active local account's Recovery Key (bech32 nsec format).
    ///
    /// Returns nil when no local account is signed in (remote signer, NIP-46, etc.).
    /// MUST NOT be logged. Display only in the explicit "Back up account" flow.
    func activeAccountRecoveryKey() -> String? {
        guard let app = appPtr else { return nil }
        guard let res = olas_active_account_recovery_key(app) else { return nil }
        defer { nmp_free_string(res) }
        return String(cString: res)
    }

    // MARK: - P0-A: follow-pack discovery and apply

    /// Open the kind:30000 follow-pack discovery interest.
    func openFollowPackDiscovery(consumer: String = "olas.follow_packs") {
        guard let app = appPtr else { return }
        consumer.withCString { olas_open_follow_pack_discovery(app, $0) }
    }

    /// Close the follow-pack discovery interest.
    func closeFollowPackDiscovery(consumer: String = "olas.follow_packs") {
        guard let app = appPtr else { return }
        consumer.withCString { olas_close_follow_pack_discovery(app, $0) }
    }

    /// Decode a raw kind:30000 event JSON into a FollowPackDescriptor.
    func decodeFollowPackEvent(_ eventJSON: String) -> FollowPackDescriptor? {
        let ptr = eventJSON.withCString { olas_decode_follow_pack_event_json($0) }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        let json = String(cString: ptr)
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(FollowPackDescriptor.self, from: data)
    }

    /// Apply selected follow packs: dispatch nmp.follow for each pubkey in
    /// the deduplicated union. Returns the result descriptor or nil on failure.
    func applyFollowPackPubkeys(_ pubkeys: [String]) -> FollowPackApplyResult? {
        guard let app = appPtr, !pubkeys.isEmpty else { return nil }
        guard let data = try? JSONSerialization.data(withJSONObject: pubkeys),
              let json = String(data: data, encoding: .utf8) else { return nil }
        let activePk = activeAccountPubkey ?? ""
        let ptr = json.withCString { pubkeysPtr in
            activePk.withCString { activePtr in
                olas_apply_follow_pack_pubkeys(app, pubkeysPtr, activePtr)
            }
        }
        guard let ptr else { return nil }
        defer { nmp_free_string(ptr) }
        let resultJSON = String(cString: ptr)
        guard let resultData = resultJSON.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(FollowPackApplyResult.self, from: resultData)
    }

    /// Live "Following" count for the active account — the number of distinct
    /// `p` tags in its current kind:3, read synchronously from the local event
    /// store (read-your-writes; reflects a just-applied follow pack with no
    /// relay round-trip). Returns 0 when unavailable / no list yet.
    func activeFollowingCount() -> Int {
        guard let app = appPtr else { return 0 }
        let n = nmp_app_active_following_count(app)
        return n < 0 ? 0 : Int(n)
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
        let payload = ["uri": uri]
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8) else { return }
        _ = dispatchAction(namespace: "nmp.wallet.connect", json: json)
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
