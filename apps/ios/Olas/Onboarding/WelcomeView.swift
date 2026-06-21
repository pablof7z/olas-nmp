import SwiftUI

struct WelcomeView: View {
    let vm: OnboardingViewModel

    private let avatarURLs = (1...30).map { "https://i.pravatar.cc/150?img=\($0)" }

    var body: some View {
        ZStack {
            // Mosaic background
            mosaicBackground
                .blur(radius: 24)
                .ignoresSafeArea()

            // Dark radial veil
            RadialGradient(
                colors: [.clear, .black.opacity(0.8), .black],
                center: .center,
                startRadius: 80,
                endRadius: 320
            )
            .ignoresSafeArea()

            // Content
            VStack(spacing: 0) {
                // P2-A: "Invited you" banner — only shown when launched via an invite link.
                if vm.inviterPubkey != nil {
                    InviteBanner()
                        .padding(.top, OlasSpacing.xl)
                        .padding(.horizontal, OlasSpacing.xl)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }

                Spacer()

                VStack(spacing: OlasSpacing.md) {
                    Text("olas")
                        .font(OlasFont.wordmark())
                        .foregroundStyle(Color.olasText1)
                        .tracking(-2)

                    Text("Your photos. Your network. No algorithm.")
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText2)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, OlasSpacing.xxl)
                }

                Spacer()

                VStack(spacing: OlasSpacing.sm) {
                    Button {
                        vm.advance(to: .createAccount)
                    } label: {
                        Text("Get started")
                            .font(OlasFont.headline())
                            .foregroundStyle(Color.olasBackground)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(OlasPressedButtonStyle())
                    .padding(.horizontal, OlasSpacing.xl)

                    Button {
                        vm.advance(to: .signIn)
                    } label: {
                        Text("I have an account")
                            .font(OlasFont.subheadline())
                            .foregroundStyle(Color.olasText2)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color(hex: "#3A3A3A"), lineWidth: 1.5)
                            )
                    }
                    .buttonStyle(OlasPressedButtonStyle())
                    .padding(.horizontal, OlasSpacing.xl)
                }
                .padding(.bottom, OlasSpacing.xl)
            }
        }
        .onAppear { vm.consumePendingInvite() }
    }

    private var mosaicBackground: some View {
        GeometryReader { geo in
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(72), spacing: 8), count: 5), spacing: 8) {
                ForEach(Array(avatarURLs.prefix(25).enumerated()), id: \.offset) { _, url in
                    CachedImage(url: URL(string: url)) {
                        Circle().fill(Color.olasSurface)
                    }
                    .frame(width: 72, height: 72)
                    .clipShape(Circle())
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

// MARK: - Invite banner

private struct InviteBanner: View {
    var body: some View {
        HStack(spacing: OlasSpacing.sm) {
            Image(systemName: "envelope.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.olasBlue)

            // Title only — never render the inviter's npub/pubkey (no-jargon rule).
            Text("A friend invited you")
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText1)

            Spacer()
        }
        .padding(OlasSpacing.md)
        .background(Color.olasSurface2, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.olasBorder, lineWidth: 1))
    }
}
