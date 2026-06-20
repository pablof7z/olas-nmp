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
    private let decoder = JSONDecoder()

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
            NMPBridge.shared.addPhotoFeedHandler { [weak self] key, json in
                self?.handleFeedSnapshot(key: key, json: json)
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

    private func handleFeedSnapshot(key: String, json: String) {
        guard key == feedKey(for: mode),
              let data = json.data(using: .utf8),
              let nextPosts = try? decoder.decode([PhotoPost].self, from: data) else { return }
        posts = nextPosts
        seenIds = Set(nextPosts.map(\.id))
        pendingPosts = []
        pendingNewCount = 0
        isLoading = false
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
        NMPBridge.shared.loadOlderFeed(key: feedKey(for: mode))
    }

    private func feedKey(for mode: FeedMode) -> String {
        mode == .following ? NMPBridge.followingFeedKey : NMPBridge.networkFeedKey
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
