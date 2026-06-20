import Foundation

@Observable @MainActor
final class FeedViewModel {
    var posts: [PhotoPost] = []
    var mode: FeedMode = .following
    var isLoading = false
    var pendingNewCount = 0
    private var pendingPosts: [PhotoPost] = []
    private var handlerRegistered = false

    func start(mode: FeedMode) {
        let shouldReset = !handlerRegistered || self.mode != mode
        self.mode = mode
        if shouldReset {
            self.posts = []
            self.pendingNewCount = 0
            self.pendingPosts = []
            isLoading = true
        }

        // Register only once — each call to start() must not stack handlers.
        if !handlerRegistered {
            handlerRegistered = true
            NMPBridge.shared.addPhotoFeedUpdateHandler { [weak self] key, posts in
                self?.handlePhotoFeedSnapshot(key: key, posts: posts)
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
        applyCurrentProjectionIfLoaded()
    }

    func handlePhotoFeedSnapshot(key: String, posts snapshotPosts: [PhotoPost]) {
        guard key == currentFeedKey else { return }
        var nextPosts = snapshotPosts
        applyProfileCache(NMPBridge.shared.profileCache, to: &nextPosts)
        for post in nextPosts {
            NMPBridge.shared.claimProfile(pubkey: post.authorPubkey, consumer: "olas.feed")
        }
        posts = nextPosts
        pendingPosts = []
        pendingNewCount = 0
        isLoading = false
    }

    // Apply profile cache updates to all feed posts (called when snapshot delivers profiles).
    private func applyProfileCache(_ cache: [String: ProfileWire]) {
        applyProfileCache(cache, to: &posts)
        applyProfileCache(cache, to: &pendingPosts)
    }

    private func applyProfileCache(_ cache: [String: ProfileWire], to target: inout [PhotoPost]) {
        func apply(to post: inout PhotoPost, cached: ProfileWire) {
            let name = cached.display
            if !name.isEmpty { post.authorName = name }
            if let a = cached.pictureUrl, !a.isEmpty { post.authorAvatar = a }
        }
        for i in target.indices {
            if let cached = cache[target[i].authorPubkey] { apply(to: &target[i], cached: cached) }
        }
    }

    func revealNewPosts() {
        posts = pendingPosts + posts
        pendingPosts = []
        pendingNewCount = 0
    }

    func refresh() {
        posts = []
        pendingNewCount = 0
        pendingPosts = []
        isLoading = true
        // Re-open the subscription without re-registering the handler.
        // NMP is always running when the user can pull-to-refresh.
        openFeed()
    }

    func loadMore() {
        NMPBridge.shared.loadOlderFeed(key: currentFeedKey)
    }

    private var currentFeedKey: String {
        mode == .following ? "olas.following_feed" : "olas.network_feed"
    }

    private func applyCurrentProjectionIfLoaded() {
        guard let snapshot = NMPBridge.shared.currentPhotoFeed(key: currentFeedKey),
              !snapshot.isEmpty else { return }
        handlePhotoFeedSnapshot(key: currentFeedKey, posts: snapshot)
    }

    // NMP-GAP(#24): Reaction state will be updated by the Rust photo-feed projection.
    // Do NOT add optimistic mutations — Rust is the single source of truth.
    func toggleLike(postId: String) {
        let json = "{\"event_id\":\"\(postId)\"}"
        _ = NMPBridge.shared.dispatchAction(namespace: "nmp.react", json: json)
    }

    // NMP-GAP(#24): Bookmark requires a Rust bookmark projection. Do NOT optimistically
    // mutate isBookmarked — the action will be wired once the projection exists.
    func toggleBookmark(postId: String) {
        // Intentionally empty: wired once the NMP bookmark projection ships.
    }
}
