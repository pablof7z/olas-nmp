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
        posts = []
        seenIds = []
        pendingNewCount = 0
        pendingPosts = []
        isLoading = true

        if !handlerRegistered {
            handlerRegistered = true
            NMPBridge.shared.addEventHandler { [weak self] json in
                self?.handleEvent(json)
            }
            NMPBridge.shared.addProfileUpdateHandler { [weak self] cache in
                self?.applyProfileCache(cache)
            }
        }

        openFeed(mode: mode)
    }

    private func openFeed(mode: FeedMode) {
        let targetMode = mode
        NMPBridge.shared.whenRunning {
            switch targetMode {
            case .following:
                NMPBridge.shared.openFollowingFeed()
            case .network:
                NMPBridge.shared.openNetworkFeed()
            }
        }
    }

    func handleEvent(_ json: String) {
        if var post = NMPBridge.shared.photoPost(from: json, mode: mode) {
            guard !seenIds.contains(post.id) else { return }
            seenIds.insert(post.id)
            isLoading = false
            if let cached = NMPBridge.shared.profileCache[post.authorPubkey] {
                post.authorName = cached.name.isEmpty ? nil : cached.name
                post.authorAvatar = cached.avatar
            }
            NMPBridge.shared.claimProfile(pubkey: post.authorPubkey, consumer: "olas.feed")
            insert(post)
            return
        }

        guard let profile = NMPBridge.shared.profile(from: json) else { return }
        let name = profile.displayName ?? profile.name
        let avatar = profile.picture
        guard name != nil || avatar != nil else { return }
        applyProfile(pubkey: profile.pubkey, name: name, avatar: avatar)
    }

    private func insert(_ post: PhotoPost) {
        if posts.isEmpty {
            posts.insert(post, at: 0)
        } else {
            pendingPosts.insert(post, at: 0)
            pendingNewCount = pendingPosts.count
        }
    }

    private func applyProfile(pubkey: String, name: String?, avatar: String?) {
        func apply(to post: inout PhotoPost) {
            if let name { post.authorName = name }
            if let avatar { post.authorAvatar = avatar }
        }
        for index in posts.indices where posts[index].authorPubkey == pubkey {
            apply(to: &posts[index])
        }
        for index in pendingPosts.indices where pendingPosts[index].authorPubkey == pubkey {
            apply(to: &pendingPosts[index])
        }
    }

    private func applyProfileCache(_ cache: [String: (name: String, avatar: String?)]) {
        for index in posts.indices {
            if let cached = cache[posts[index].authorPubkey] {
                applyCachedProfile(cached, to: &posts[index])
            }
        }
        for index in pendingPosts.indices {
            if let cached = cache[pendingPosts[index].authorPubkey] {
                applyCachedProfile(cached, to: &pendingPosts[index])
            }
        }
    }

    private func applyCachedProfile(_ cached: (name: String, avatar: String?), to post: inout PhotoPost) {
        if !cached.name.isEmpty { post.authorName = cached.name }
        if let avatar = cached.avatar { post.authorAvatar = avatar }
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
        openFeed(mode: mode)
    }

    func loadMore() {
        let feedKey = mode == .following ? "olas.following_feed" : "olas.network_feed"
        NMPBridge.shared.loadOlderFeed(key: feedKey)
    }

    func toggleLike(postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        guard !posts[index].isLiked else { return }
        _ = NMPBridge.shared.react(to: posts[index])
    }

    func toggleBookmark(postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        let shouldAdd = !posts[index].isBookmarked
        _ = NMPBridge.shared.bookmark(post: posts[index], add: shouldAdd)
    }
}
