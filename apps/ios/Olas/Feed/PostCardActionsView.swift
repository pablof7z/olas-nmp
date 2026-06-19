import SwiftUI

struct PostCardActionsView: View {
    let post: PhotoPost
    let vm: FeedViewModel
    @State private var showZapSheet = false
    @State private var likeScale: CGFloat = 1

    var body: some View {
        HStack(spacing: 0) {
            HStack(spacing: 24) {
                // Like
                actionButton(
                    icon: post.isLiked ? "heart.fill" : "heart",
                    color: post.isLiked ? .olasHeart : .olasText2,
                    label: "Like"
                ) {
                    withAnimation(.olasBouncy, completionCriteria: .logicallyComplete) {
                        likeScale = 1.35
                    } completion: {
                        withAnimation(.olasBouncy) { likeScale = 1.0 }
                    }
                    vm.toggleLike(postId: post.id)
                }
                .scaleEffect(likeScale)

                actionButton(icon: "bubble.right", color: .olasText2, label: "Comment") {}

                actionButton(
                    icon: "bolt",
                    color: .olasText2,
                    label: "Zap"
                ) {
                    showZapSheet = true
                }

                actionButton(icon: "arrow.up", color: .olasText2, label: "Share") {
                    sharePost()
                }
            }

            Spacer()

            // Bookmark
            actionButton(
                icon: post.isBookmarked ? "bookmark.fill" : "bookmark",
                color: post.isBookmarked ? .olasText1 : .olasText2,
                label: "Save"
            ) {
                vm.toggleBookmark(postId: post.id)
            }
            .padding(.trailing, OlasSpacing.md)
        }
        .padding(.horizontal, OlasSpacing.md)
        .padding(.vertical, OlasSpacing.sm)
        .frame(height: 52)
        .sheet(isPresented: $showZapSheet) {
            ZapSheet(post: post)
                .presentationDetents([.medium])
        }
    }

    private func actionButton(
        icon: String,
        color: Color,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .regular))
                .foregroundStyle(color)
                .frame(width: 44, height: 44)
                .accessibilityLabel(label)
        }
        .buttonStyle(OlasPressedButtonStyle())
    }

    private func sharePost() {
        let url = "https://njump.me/\(post.id)"
        let av = UIActivityViewController(activityItems: [URL(string: url)!], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            root.present(av, animated: true)
        }
    }
}

// MARK: - Zap Sheet

struct ZapSheet: View {
    let post: PhotoPost
    @Environment(\.dismiss) private var dismiss
    @State private var selectedAmount: Int64 = 21
    @State private var comment = ""

    private let presets: [Int64] = [21, 100, 500, 1000]

    var body: some View {
        VStack(spacing: OlasSpacing.xl) {
            Text("Zap")
                .font(OlasFont.title2())
                .foregroundStyle(Color.olasText1)
                .padding(.top, OlasSpacing.md)

            HStack(spacing: OlasSpacing.sm) {
                ForEach(presets, id: \.self) { amount in
                    Button {
                        selectedAmount = amount
                    } label: {
                        Text("\(amount)")
                            .font(OlasFont.callout())
                            .foregroundStyle(selectedAmount == amount ? Color.olasBackground : Color.olasText1)
                            .padding(.horizontal, OlasSpacing.md)
                            .padding(.vertical, OlasSpacing.xs)
                            .background(
                                selectedAmount == amount ? Color.olasZap : Color.olasSurface,
                                in: Capsule()
                            )
                    }
                    .buttonStyle(OlasPressedButtonStyle())
                }
            }

            TextField("Add a comment (optional)", text: $comment)
                .font(OlasFont.body())
                .foregroundStyle(Color.olasText1)
                .padding(OlasSpacing.md)
                .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, OlasSpacing.xl)

            Button {
                sendZap()
                dismiss()
            } label: {
                HStack(spacing: OlasSpacing.xs) {
                    Image(systemName: "bolt.fill")
                    Text("Zap \(selectedAmount) sats")
                }
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasBackground)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(Color.olasZap, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(OlasPressedButtonStyle())
            .padding(.horizontal, OlasSpacing.xl)

            Spacer()
        }
        .background(Color.olasSurface2)
    }

    private func sendZap() {
        if let actionJSON = NMPBridge.shared.buildZapActionJSON(eventId: post.id, sats: selectedAmount) {
            _ = NMPBridge.shared.dispatchAction(namespace: "nmp.nip57.zap", json: actionJSON)
        }
    }
}
