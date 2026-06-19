import SwiftUI

struct ProfileHeaderView: View {
    let profile: OlasProfile
    let isOwn: Bool
    let followingCount: Int
    let followerCount: Int

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
                        HStack(spacing: OlasSpacing.xs) {
                            Button {
                                if isFollowing {
                                    bridge.unfollow(pubkey: profile.pubkey)
                                    isFollowing = false
                                } else {
                                    bridge.follow(pubkey: profile.pubkey)
                                    isFollowing = true
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

                            Button {
                                // Zap
                            } label: {
                                Image(systemName: "bolt")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundStyle(Color.olasText1)
                                    .frame(width: 36, height: 36)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(Color.olasBorder, lineWidth: 1.5)
                                    )
                            }
                            .buttonStyle(OlasPressedButtonStyle())
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

                    // Trust line (others only)
                    if !isOwn {
                        Text("Followed by people you follow")
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)
                            .padding(.top, 2)
                    }

                    // Stats row
                    HStack(spacing: OlasSpacing.xl) {
                        statItem(count: followingCount, label: "Following")
                        statItem(count: followerCount, label: "Followers")
                    }
                    .padding(.top, OlasSpacing.xs)
                }
                .padding(.horizontal, OlasSpacing.md)
                .padding(.top, -24) // compensate avatar offset
            }
        }
    }

    private func statItem(count: Int, label: String) -> some View {
        VStack(spacing: 1) {
            Text("\(count)")
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasText1)
                .monospacedDigit()
            Text(label)
                .font(OlasFont.caption())
                .foregroundStyle(Color.olasText2)
        }
    }
}
