import SwiftUI

struct SuggestedAccountCard: View {
    let profile: OlasProfile
    /// Social proof preloaded by the parent (Discover / Search). May be nil when
    /// the card is rendered before social-proof data arrives.
    let socialProof: SocialProof?

    @State private var isFollowing = false
    private var bridge: NMPBridge { NMPBridge.shared }

    init(profile: OlasProfile, socialProof: SocialProof? = nil) {
        self.profile = profile
        self.socialProof = socialProof
    }

    var body: some View {
        VStack(spacing: 0) {
            // Avatar
            CachedImage(url: URL(string: profile.picture ?? "")) {
                Circle().fill(Color.olasSurface2)
            }
            .frame(width: 72, height: 72)
            .clipShape(Circle())
            .padding(.top, OlasSpacing.md)

            VStack(spacing: OlasSpacing.xxs) {
                Text(profile.displayNameOrName)
                    .font(OlasFont.feedUsername())
                    .foregroundStyle(Color.olasText1)
                    .lineLimit(1)

                // Social proof line — truthful or honest fallback.
                if let proof = socialProof {
                    SocialProofRow(proof: proof)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity)
                }

                Button {
                    if isFollowing {
                        bridge.unfollow(pubkey: profile.pubkey)
                    } else {
                        bridge.follow(pubkey: profile.pubkey)
                    }
                    isFollowing = bridge.isFollowing(profile.pubkey)
                } label: {
                    Text(isFollowing ? "Following" : "Follow")
                        .font(OlasFont.caption())
                        .foregroundStyle(isFollowing ? Color.olasText2 : Color.olasBackground)
                        .padding(.horizontal, OlasSpacing.md)
                        .padding(.vertical, 6)
                        .background(
                            isFollowing ? Color.olasSurface2 : Color.olasText1,
                            in: RoundedRectangle(cornerRadius: 8)
                        )
                }
                .buttonStyle(OlasPressedButtonStyle())
                .animation(.olasStandard, value: isFollowing)
                .onAppear {
                    isFollowing = bridge.isFollowing(profile.pubkey)
                }
                .onChange(of: bridge.followedPubkeys) { _, _ in
                    isFollowing = bridge.isFollowing(profile.pubkey)
                }
            }
            .padding(.vertical, OlasSpacing.sm)
            .padding(.horizontal, OlasSpacing.sm)
        }
        .frame(width: 120)
        .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.olasBorder, lineWidth: 1))
        .clipped()
    }
}
