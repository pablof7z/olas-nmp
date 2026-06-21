import SwiftUI

struct PostCardView: View {
    let post: PhotoPost
    let vm: FeedViewModel
    var onImageTap: ((Int) -> Void)?

    @State private var currentImageIndex: Int? = 0
    @State private var showHeartBurst = false
    @State private var heartBurstLocation: CGPoint = .zero
    @State private var isExpanded = false
    @Environment(\.zoomNamespace) private var zoomNamespace
    @Environment(\.activeZoomId) private var activeZoomId

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            PostCardHeaderView(post: post)

            imageSection

            PostCardActionsView(post: post, vm: vm)

            metadataSection
        }
        .background(Color.olasBackground)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Image Section

    private var imageSection: some View {
        GeometryReader { geo in
            ZStack {
                if post.images.count == 1 {
                    singleImage(post.images[0], width: geo.size.width)
                        .onTapGesture { onImageTap?(0) }
                        .onTapGesture(count: 2) { _ in
                            triggerHeartBurst(at: CGPoint(x: geo.size.width / 2, y: geo.size.height / 2))
                            vm.toggleLike(postId: post.id)
                        }
                } else {
                    carouselImages(width: geo.size.width)
                }

                if showHeartBurst {
                    heartBurstOverlay
                        .position(heartBurstLocation)
                }
            }
        }
        .aspectRatio(imageAspectRatio, contentMode: .fit)
    }

    @ViewBuilder
    private func singleImage(_ image: ImageMeta, width: CGFloat) -> some View {
        let sourceId = "feed-\(post.id)-0"
        CachedImage(url: URL(string: image.url), meta: image)
            .frame(width: width)
            .clipped()
            // Yield source ownership when this thumbnail is the active lift so
            // the fullscreen image (isSource: true) controls its own full-screen frame.
            .zoomSource(id: sourceId, namespace: zoomNamespace, isLiftActive: activeZoomId == sourceId)
    }

    private func carouselImages(width: CGFloat) -> some View {
        ZStack(alignment: .bottom) {
            // Custom horizontal pager — avoids UIKitAdaptableTabView accessibility
            // overhead that caused 100% CPU from synchronous string-table I/O.
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(Array(post.images.enumerated()), id: \.offset) { idx, image in
                        CachedImage(url: URL(string: image.url), meta: image)
                            .frame(width: width)
                            .clipped()
                            // Each page is an independent zoom source; yield when active.
                            .zoomSource(id: "feed-\(post.id)-\(idx)", namespace: zoomNamespace,
                                        isLiftActive: activeZoomId == "feed-\(post.id)-\(idx)")
                            .onTapGesture { onImageTap?(idx) }
                    }
                }
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $currentImageIndex)

            if post.images.count > 1 {
                HStack(spacing: 5) {
                    ForEach(0..<post.images.count, id: \.self) { i in
                        Circle()
                            .fill(i == (currentImageIndex ?? 0) ? Color.white : Color.white.opacity(0.5))
                            .frame(width: 6, height: 6)
                    }
                }
                .padding(.bottom, OlasSpacing.sm)
            }
        }
    }

    private var heartBurstOverlay: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: 80))
            .foregroundStyle(Color.olasHeart)
            .shadow(color: .black.opacity(0.3), radius: 8)
            .scaleEffect(showHeartBurst ? 1 : 0.1)
            .opacity(showHeartBurst ? 1 : 0)
            .transition(.scale.combined(with: .opacity))
    }

    private func triggerHeartBurst(at point: CGPoint) {
        OlasHaptics.impactLight()
        heartBurstLocation = point
        withAnimation(.olasBouncy, completionCriteria: .logicallyComplete) {
            showHeartBurst = true
        } completion: {
            withAnimation(.easeOut(duration: 0.3)) { showHeartBurst = false }
        }
    }

    private var imageAspectRatio: CGFloat {
        guard let first = post.images.first,
              let w = first.dimensions?.width, let h = first.dimensions?.height, w > 0, h > 0 else {
            return 4.0 / 5.0 // default portrait
        }
        let ratio = CGFloat(w) / CGFloat(h)
        if ratio > 1.5 { return 3.0 / 2.0 }   // landscape capped at 3:2
        if ratio < 0.8 { return 4.0 / 5.0 }   // portrait capped at 4:5
        return ratio
    }

    // MARK: - Metadata Section

    private var metadataSection: some View {
        VStack(alignment: .leading, spacing: 3) {
            if post.reactionCount > 0 {
                Text("\(post.reactionCount) reactions")
                    .font(OlasFont.feedReactionCount())
                    .foregroundStyle(Color.olasText1)
            }

            if !post.caption.isEmpty {
                captionText
            }

            if post.commentCount > 0 {
                Text("View \(post.commentCount) comments")
                    .font(OlasFont.feedCaption())
                    .foregroundStyle(Color.olasText2)
            }
        }
        .padding(.horizontal, OlasSpacing.sm)
        .padding(.top, OlasSpacing.xs)
        .padding(.bottom, OlasSpacing.md)
    }

    private var captionText: some View {
        let name = post.authorName ?? String(post.authorPubkey.prefix(8))
        let caption = post.caption
        let lineLimit = isExpanded ? nil : 2
        return VStack(alignment: .leading, spacing: 0) {
            Text("**\(name)** \(caption)")
                .font(OlasFont.feedCaption())
                .foregroundStyle(Color.olasText1)
                .lineLimit(lineLimit)
                .truncationMode(.tail)

            if !isExpanded && caption.count > 80 {
                Text("more")
                    .font(OlasFont.feedCaption())
                    .foregroundStyle(Color.olasText3)
            }
        }
        .onTapGesture { isExpanded.toggle() }
    }
}
