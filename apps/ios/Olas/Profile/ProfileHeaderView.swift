import SwiftUI

struct ProfileHeaderView: View {
    let profile: OlasProfile
    let isOwn: Bool

    @State private var isFollowing: Bool = false

    private var bridge: NMPBridge { NMPBridge.shared }

    var body: some View {
        VStack(spacing: 0) {
            // Banner
            AsyncImage(url: URL(string: profile.banner ?? "")) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                Rectangle().fill(Color.olasSurface2)
            }
            .frame(maxWidth: .infinity, minHeight: 160, maxHeight: 160)
            .clipped()

            VStack(alignment: .leading, spacing: OlasSpacing.sm) {
                HStack(alignment: .bottom) {
                    // Avatar
                    NostrAvatar(
                        pubkey: profile.pubkey,
                        pictureUrl: profile.picture.flatMap { URL(string: $0) },
                        size: 80
                    )
                    .overlay(Circle().stroke(Color.olasBackground, lineWidth: 3))
                    .offset(y: -32)

                    Spacer()

                    if isOwn {
                        NavigationLink(destination: EditProfileView(profile: profile)) {
                            Text("Edit profile")
                                .font(OlasFont.subheadline())
                                .foregroundStyle(Color.olasText1)
                                .padding(.horizontal, OlasSpacing.md)
                                .padding(.vertical, OlasSpacing.xs)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.olasBorder, lineWidth: 1.5)
                                )
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            if isFollowing {
                                bridge.unfollow(pubkey: profile.pubkey)
                            } else {
                                bridge.follow(pubkey: profile.pubkey)
                            }
                        } label: {
                            Text(isFollowing ? "Following" : "Follow")
                                .font(OlasFont.subheadline())
                                .foregroundStyle(isFollowing ? Color.olasText1 : Color.olasBackground)
                                .padding(.horizontal, OlasSpacing.md)
                                .padding(.vertical, OlasSpacing.xs)
                                .background(
                                    isFollowing ? Color.clear : Color.olasText1,
                                    in: RoundedRectangle(cornerRadius: 12)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(isFollowing ? Color.olasBorder : Color.clear, lineWidth: 1.5)
                                )
                        }
                        .buttonStyle(OlasPressedButtonStyle())
                        .onAppear {
                            isFollowing = bridge.isFollowing(profile.pubkey)
                        }
                        .onChange(of: bridge.followedPubkeys) { _, _ in
                            isFollowing = bridge.isFollowing(profile.pubkey)
                        }
                    }
                }
                .padding(.horizontal, OlasSpacing.md)

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: OlasSpacing.xs) {
                        Text(profile.displayNameOrName)
                            .font(OlasFont.title2())
                            .foregroundStyle(Color.olasText1)

                        if profile.nip05 != nil {
                            Image(systemName: "checkmark")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.olasText2)
                        }
                    }

                    if let nip05 = profile.nip05 {
                        Text(nip05)
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)
                    }

                    if let about = profile.about {
                        Text(about)
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                            .lineLimit(2)
                            .padding(.top, 2)
                    }

                }
                .padding(.horizontal, OlasSpacing.md)
                .padding(.top, -24) // compensate avatar offset
            }
        }
    }
}
