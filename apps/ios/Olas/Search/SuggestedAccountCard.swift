import SwiftUI

struct SuggestedAccountCard: View {
    let profile: OlasProfile
    @State private var isFollowing = false

    var body: some View {
        VStack(spacing: 0) {
            // Avatar
            AsyncImage(url: URL(string: profile.picture ?? "")) { img in
                img.resizable().scaledToFill()
            } placeholder: {
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

                Button {
                    isFollowing.toggle()
                    let json = "{\"pubkey\":\"\(profile.pubkey)\"}"
                    _ = NMPBridge.shared.dispatchAction(
                        namespace: isFollowing ? "nmp.follow" : "nmp.unfollow",
                        json: json
                    )
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
