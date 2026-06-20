import SwiftUI
import os

private let feedViewDiag = Logger(subsystem: "io.f7z.olas", category: "feeddiag")

struct FeedView: View {
    @State private var vm = FeedViewModel()
    @State private var showFullscreen: (post: PhotoPost, index: Int)?

    private var currentMode: FeedMode {
        NMPBridge.shared.feedMode == "following" ? .following : .network
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0) {
                    // New posts pill
                    if vm.pendingNewCount > 0 {
                        Button {
                            withAnimation(.olasStandard) {
                                vm.revealNewPosts()
                            }
                        } label: {
                            Label("\(vm.pendingNewCount) new posts", systemImage: "arrow.up")
                                .font(OlasFont.subheadline())
                                .foregroundStyle(Color.olasText1)
                                .padding(.horizontal, OlasSpacing.md)
                                .padding(.vertical, OlasSpacing.xs)
                                .background(Color(hex: "#1F1F1F"), in: Capsule())
                                .overlay(Capsule().stroke(Color.olasBorder, lineWidth: 1))
                                .shadow(color: .black.opacity(0.4), radius: 8, y: 4)
                        }
                        .buttonStyle(OlasPressedButtonStyle())
                        .padding(.top, OlasSpacing.sm)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    ForEach(vm.posts) { post in
                        PostCardView(post: post, vm: vm, onImageTap: { idx in
                            showFullscreen = (post, idx)
                        })
                        .onAppear {
                            if post.id == vm.posts.last?.id {
                                vm.loadMore()
                            }
                        }
                        Rectangle()
                            .fill(Color.olasBackground)
                            .frame(height: 8)
                    }

                    if vm.isLoading {
                        FeedSkeletonView()
                    } else if vm.posts.isEmpty {
                        Text("Nothing here yet")
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText2)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 80)
                    }
                }
            }
            .refreshable { vm.refresh() }
            .background(Color.olasBackground)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("olas")
                        .font(.system(size: 24, weight: .black))
                        .foregroundStyle(Color.olasText1)
                        .tracking(-1)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    feedPickerMenu
                }
            }
            .fullScreenCover(item: Binding(
                get: { showFullscreen.map { p in FullscreenImageItem(post: p.post, index: p.index) } },
                set: { _ in showFullscreen = nil }
            )) { item in
                FullscreenImageView(post: item.post, initialIndex: item.index)
            }
        }
        .task {
            feedViewDiag.error("FEEDDIAG FeedView.task fired vmId=\(vm.diagId) isRunning=\(NMPBridge.shared.isRunning)")
            vm.start(mode: currentMode)
            // If NMP was already running when the view appeared (e.g. tab revisit),
            // open the feed immediately.
            if NMPBridge.shared.isRunning { vm.openFeed() }
        }
        .onChange(of: NMPBridge.shared.isRunning) { _, running in
            feedViewDiag.error("FEEDDIAG FeedView.onChange isRunning=\(running) vmId=\(vm.diagId)")
            // Open the feed the moment the bridge transitions to running — zero polling.
            if running { vm.openFeed() }
        }
    }

    private var feedPickerMenu: some View {
        Menu {
            Button { switchMode(.following) } label: {
                Label("Following", systemImage: currentMode == .following ? "checkmark" : "")
            }
            Button { switchMode(.network) } label: {
                Label("Your extended network", systemImage: currentMode == .network ? "checkmark" : "")
            }
        } label: {
            HStack(spacing: 4) {
                Text(currentMode == .following ? "Following" : "Network")
                    .font(OlasFont.subheadline())
                    .foregroundStyle(Color.olasText2)
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.olasText3)
            }
        }
    }

    private func switchMode(_ mode: FeedMode) {
        guard mode != currentMode else { return }
        NMPBridge.shared.setFeedMode(mode == .following ? "following" : "network")
        vm.start(mode: mode)
        // NMP is always running when the user can interact with the mode picker.
        vm.openFeed()
    }
}

// Helper for fullscreen cover
private struct FullscreenImageItem: Identifiable {
    let id = UUID()
    let post: PhotoPost
    let index: Int
}

// MARK: - Skeleton

struct FeedSkeletonView: View {
    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<3, id: \.self) { _ in
                SkeletonCard()
                Rectangle().fill(Color.olasBackground).frame(height: 8)
            }
        }
    }
}

// Each card gets its own shimmer phase so sibling cards animate independently.
private struct SkeletonCard: View {
    @State private var shimmerPhase: CGFloat = -1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header row
            HStack(spacing: OlasSpacing.sm) {
                Circle()
                    .fill(Color.olasSurface2)
                    .frame(width: 32, height: 32)
                    .shimmer(phase: shimmerPhase, reduceMotion: reduceMotion)
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.olasSurface2)
                    .frame(width: 100, height: 14)
                    .shimmer(phase: shimmerPhase, reduceMotion: reduceMotion)
                Spacer()
            }
            .padding(.horizontal, OlasSpacing.sm)
            .padding(.vertical, OlasSpacing.sm)

            // Image placeholder — 4:5 aspect ratio matches feed default
            Rectangle()
                .fill(Color.olasSurface2)
                .frame(maxWidth: .infinity)
                .aspectRatio(4.0 / 5.0, contentMode: .fill)
                .shimmer(phase: shimmerPhase, reduceMotion: reduceMotion)

            // Caption lines
            VStack(alignment: .leading, spacing: OlasSpacing.xs) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.olasSurface2)
                    .frame(width: 120, height: 13)
                    .shimmer(phase: shimmerPhase, reduceMotion: reduceMotion)
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.olasSurface2)
                    .frame(width: 200, height: 13)
                    .shimmer(phase: shimmerPhase, reduceMotion: reduceMotion)
            }
            .padding(OlasSpacing.sm)
        }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(
                .linear(duration: 1.4).repeatForever(autoreverses: false)
            ) {
                shimmerPhase = 1
            }
        }
    }
}

// MARK: - Shimmer modifier

private extension View {
    /// Sweeps a highlight band left → right over the receiver.
    /// When `reduceMotion` is true the modifier is a no-op (static surface only).
    @ViewBuilder
    func shimmer(phase: CGFloat, reduceMotion: Bool) -> some View {
        if reduceMotion {
            self
        } else {
            self.overlay(
                GeometryReader { geo in
                    let w = geo.size.width
                    LinearGradient(
                        stops: [
                            .init(color: .clear, location: 0),
                            .init(color: Color.white.opacity(0.10), location: 0.4),
                            .init(color: Color.white.opacity(0.18), location: 0.5),
                            .init(color: Color.white.opacity(0.10), location: 0.6),
                            .init(color: .clear, location: 1),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: w * 2)
                    .offset(x: phase * w * 2 - w)
                }
                .clipped()
            )
        }
    }
}
