import SwiftUI

struct PostCardHeaderView: View {
    let post: PhotoPost

    @State private var isFollowing: Bool = false

    private var bridge: NMPBridge { NMPBridge.shared }

    private var showFollowButton: Bool {
        guard let activePubkey = bridge.activeAccountPubkey else { return false }
        return post.authorPubkey != activePubkey
    }

    var body: some View {
        HStack(spacing: OlasSpacing.sm) {
            // Avatar
            NavigationLink(destination: ProfileView(pubkey: post.authorPubkey, isOwn: false)) {
                NostrAvatar(
                    pubkey: post.authorPubkey,
                    pictureUrl: post.authorAvatar.flatMap { URL(string: $0) },
                    size: 40
                )
                .overlay(Circle().stroke(Color.olasBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                NavigationLink(destination: ProfileView(pubkey: post.authorPubkey, isOwn: false)) {
                    NostrProfileName(
                        pubkey: post.authorPubkey,
                        font: OlasFont.feedUsername(),
                        color: Color.olasText1
                    )
                }
                .buttonStyle(.plain)
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
        }
        .padding(.horizontal, OlasSpacing.md)
        .frame(height: 52)
    }
}
