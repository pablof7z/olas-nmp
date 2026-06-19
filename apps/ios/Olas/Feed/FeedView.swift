import SwiftUI

struct FeedView: View {
    @State private var vm = FeedViewModel()
    // NMP-GAP(#28): Feed mode state must be owned by a Rust projection, not native @State.
    @State private var selectedMode: FeedMode = .network
    @State private var showFullscreen: (post: PhotoPost, index: Int)?

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
        .onAppear { vm.start(mode: selectedMode) }
    }

    private var feedPickerMenu: some View {
        Menu {
            Button { switchMode(.following) } label: {
                Label("Following", systemImage: selectedMode == .following ? "checkmark" : "")
            }
            Button { switchMode(.network) } label: {
                Label("Your extended network", systemImage: selectedMode == .network ? "checkmark" : "")
            }
        } label: {
            HStack(spacing: 4) {
                Text(selectedMode == .following ? "Following" : "Network")
                    .font(OlasFont.subheadline())
                    .foregroundStyle(Color.olasText2)
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.olasText3)
            }
        }
    }

    private func switchMode(_ mode: FeedMode) {
        guard mode != selectedMode else { return }
        selectedMode = mode
        vm.start(mode: mode)
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
                skeletonCard
                Rectangle().fill(Color.olasBackground).frame(height: 8)
            }
        }
    }

    private var skeletonCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: OlasSpacing.sm) {
                Circle().fill(Color.olasSurface2).frame(width: 32, height: 32)
                RoundedRectangle(cornerRadius: 4).fill(Color.olasSurface2).frame(width: 100, height: 14)
                Spacer()
            }
            .padding(.horizontal, OlasSpacing.sm)
            .padding(.vertical, OlasSpacing.sm)

            Rectangle()
                .fill(Color.olasSurface2)
                .frame(maxWidth: .infinity)
                .aspectRatio(4.0/5.0, contentMode: .fill)

            VStack(alignment: .leading, spacing: OlasSpacing.xs) {
                RoundedRectangle(cornerRadius: 4).fill(Color.olasSurface2).frame(width: 120, height: 13)
                RoundedRectangle(cornerRadius: 4).fill(Color.olasSurface2).frame(width: 200, height: 13)
            }
            .padding(OlasSpacing.sm)
        }
    }
}
