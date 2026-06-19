import SwiftUI

struct PostCardHeaderView: View {
    let post: PhotoPost
    var onMute: (() -> Void)?
    var onReport: (() -> Void)?

    @State private var isFollowing: Bool = false

    private var bridge: NMPBridge { NMPBridge.shared }

    private var showFollowButton: Bool {
        guard let activePubkey = bridge.activeAccountPubkey else { return false }
        return post.authorPubkey != activePubkey
    }

    // Trust line is derived from local follow graph — not a WoT score
    private var trustLine: String {
        // Placeholder until follow graph data flows from NMP
        "Followed by someone you follow"
    }

    var body: some View {
        HStack(spacing: OlasSpacing.sm) {
            // Avatar
            NavigationLink(destination: ProfileView(pubkey: post.authorPubkey, isOwn: false)) {
                AsyncImage(url: URL(string: post.authorAvatar ?? "")) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Circle().fill(Color.olasSurface2)
                }
                .frame(width: 40, height: 40)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.olasBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                NavigationLink(destination: ProfileView(pubkey: post.authorPubkey, isOwn: false)) {
                    Text(post.authorName ?? String(post.authorPubkey.prefix(8)))
                        .font(OlasFont.feedUsername())
                        .foregroundStyle(Color.olasText1)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)

                Text(trustLine)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(Color(hex: "#9A9A9A"))
                    .lineLimit(1)
            }
            .frame(maxHeight: .infinity, alignment: .center)

            Spacer()

            if showFollowButton {
                Button {
                    if isFollowing {
                        bridge.unfollow(pubkey: post.authorPubkey)
                    } else {
                        bridge.follow(pubkey: post.authorPubkey)
                    }
                    isFollowing = bridge.isFollowing(post.authorPubkey)
                } label: {
                    Text(isFollowing ? "Following" : "Follow")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(isFollowing ? Color.olasText2 : Color.olasBackground)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(
                            isFollowing
                                ? Color.clear
                                : Color.olasText1,
                            in: Capsule()
                        )
                        .overlay(
                            Capsule()
                                .stroke(isFollowing ? Color.olasBorder : Color.clear, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .onAppear {
                    isFollowing = bridge.isFollowing(post.authorPubkey)
                }
                .onChange(of: bridge.followedPubkeys) { _, _ in
                    isFollowing = bridge.isFollowing(post.authorPubkey)
                }
            }

            Text(post.createdAt.relativeTimeString)
                .font(OlasFont.feedTimestamp())
                .foregroundStyle(Color.olasText3)
                .frame(maxHeight: .infinity, alignment: .center)

            Menu {
                Button(action: { onMute?() }) {
                    Label("Mute account", systemImage: "speaker.slash")
                }
                Button(action: { onReport?() }) {
                    Label("Report", systemImage: "flag")
                }
                Button {
                    // Copy link
                } label: {
                    Label("Copy link", systemImage: "link")
                }
                Button {
                    // Why am I seeing this
                } label: {
                    Label("Why am I seeing this?", systemImage: "questionmark.circle")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16, weight: .regular))
                    .foregroundStyle(Color.olasText3)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, OlasSpacing.md)
        .frame(height: 52)
    }
}
