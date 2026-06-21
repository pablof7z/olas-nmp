import SwiftUI
import os

private let headerDiag = Logger(subsystem: "io.f7z.olas", category: "feeddiag")

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

                // Trust line: hidden until follow graph data flows from NMP

            }
            .frame(maxHeight: .infinity, alignment: .center)

            Spacer()

            if showFollowButton {
                Button {
                    headerDiag.error("FEEDDIAG Follow button tapped pubkey=\(post.authorPubkey) wasFollowing=\(isFollowing)")
                    if isFollowing {
                        bridge.unfollow(pubkey: post.authorPubkey)
                        isFollowing = false
                    } else {
                        bridge.follow(pubkey: post.authorPubkey)
                        isFollowing = true
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
