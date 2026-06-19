import Foundation

@Observable @MainActor
final class FeedViewModel {
    var posts: [PhotoPost] = []
    var mode: FeedMode = .following
    var isLoading = false
    var pendingNewCount = 0
    private var pendingPosts: [PhotoPost] = []
    private var seenIds: Set<String> = []
    private var handlerRegistered = false

    func start(mode: FeedMode) {
        self.mode = mode
        self.posts = []
        self.seenIds = []
        self.pendingNewCount = 0
        self.pendingPosts = []
        isLoading = true

        // Register only once — each call to start() must not stack handlers.
        if !handlerRegistered {
            handlerRegistered = true
            NMPBridge.shared.addEventHandler { [weak self] json in
                self?.handleEvent(json)
            }
            NMPBridge.shared.addProfileUpdateHandler { [weak self] cache in
                self?.applyProfileCache(cache)
            }
        }

        // Do not call openFeed() here. FeedView observes NMPBridge.shared.isRunning
        // and calls openFeed() the moment the bridge is ready (event-driven).
    }

    /// Open the feed subscription. Call only when NMPBridge.shared.isRunning == true.
    func openFeed() {
        switch mode {
        case .following: NMPBridge.shared.openFollowingFeed()
        case .network:   NMPBridge.shared.openNetworkFeed()
        }
    }

    func handleEvent(_ json: String) {
        guard let data = json.data(using: .utf8),
              let event = try? JSONDecoder().decode(NostrEvent.self, from: data) else { return }
        switch event.kind {
        case 20:
            guard !seenIds.contains(event.id),
                  var post = PhotoPostParser.parse(event) else { return }
            seenIds.insert(event.id)
            isLoading = false
            // Apply any already-cached profile for this author immediately.
            if let cached = NMPBridge.shared.profileCache[event.author] {
                post.authorName = cached.display.isEmpty ? nil : cached.display
                post.authorAvatar = cached.pictureUrl
            }
            // Request kind:0 profile metadata (force=1 bypasses EventStore dedup).
            NMPBridge.shared.claimProfile(pubkey: event.author, consumer: "olas.feed")
            if posts.isEmpty {
                posts.insert(post, at: 0)
            } else {
                pendingPosts.insert(post, at: 0)
                pendingNewCount = pendingPosts.count
            }
        case 0:
            // Profile metadata arrived — update any posts in feed from this author.
            guard let meta = try? JSONDecoder().decode(ProfileMeta.self, from: Data(event.content.utf8)) else { return }
            let name = meta.displayName ?? meta.name
            let avatar = meta.picture
            guard name != nil || avatar != nil else { return }
            func apply(to post: inout PhotoPost) {
                if let n = name { post.authorName = n }
                if let a = avatar { post.authorAvatar = a }
            }
            for i in posts.indices where posts[i].authorPubkey == event.author { apply(to: &posts[i]) }
            for i in pendingPosts.indices where pendingPosts[i].authorPubkey == event.author { apply(to: &pendingPosts[i]) }
        default:
            break
        }
    }

    // Minimal subset of NIP-01 kind:0 content.
    private struct ProfileMeta: Codable {
        let name: String?
        let displayName: String?
        let picture: String?
        enum CodingKeys: String, CodingKey {
            case name
            case displayName = "display_name"
            case picture
        }
    }

    // Apply profile cache updates to all feed posts (called when snapshot delivers profiles).
    private func applyProfileCache(_ cache: [String: ProfileWire]) {
        func apply(to post: inout PhotoPost, cached: ProfileWire) {
            let name = cached.display
            if !name.isEmpty { post.authorName = name }
            if let a = cached.pictureUrl, !a.isEmpty { post.authorAvatar = a }
        }
        for i in posts.indices {
            if let cached = cache[posts[i].authorPubkey] { apply(to: &posts[i], cached: cached) }
        }
        for i in pendingPosts.indices {
            if let cached = cache[pendingPosts[i].authorPubkey] { apply(to: &pendingPosts[i], cached: cached) }
        }
    }

    func revealNewPosts() {
        posts = pendingPosts + posts
        pendingPosts = []
        pendingNewCount = 0
    }

    func refresh() {
        posts = []
        seenIds = []
        pendingNewCount = 0
        pendingPosts = []
        isLoading = true
        // Re-open the subscription without re-registering the handler.
        // NMP is always running when the user can pull-to-refresh.
        openFeed()
    }

    func loadMore() {
        let feedKey = mode == .following ? "olas.following_feed" : "olas.network_feed"
        NMPBridge.shared.loadOlderFeed(key: feedKey)
    }

    func toggleLike(postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        let wasLiked = posts[index].isLiked
        posts[index].isLiked = !wasLiked
        posts[index].reactionCount += wasLiked ? -1 : 1

        if !wasLiked {
            let json = "{\"event_id\":\"\(postId)\"}"
            _ = NMPBridge.shared.dispatchAction(namespace: "nmp.react", json: json)
        }
    }

    func toggleBookmark(postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        posts[index].isBookmarked.toggle()
    }
}
