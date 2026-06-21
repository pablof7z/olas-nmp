import SwiftUI

struct OnboardingCompleteView: View {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @AppStorage("firstPostCoachmarkSeen") private var firstPostCoachmarkSeen = false
    @State private var checkmarkProgress: CGFloat = 0
    @State private var showContent = false

    var body: some View {
        VStack(spacing: OlasSpacing.xxl) {
            Spacer()

            ZStack {
                Circle()
                    .stroke(Color.olasBorder, lineWidth: 3)
                    .frame(width: 100, height: 100)

                Circle()
                    .trim(from: 0, to: checkmarkProgress)
                    .stroke(Color.olasSuccess, lineWidth: 3)
                    .frame(width: 100, height: 100)
                    .rotationEffect(.degrees(-90))

                Image(systemName: "checkmark")
                    .font(.system(size: 40, weight: .semibold))
                    .foregroundStyle(Color.olasSuccess)
                    .scaleEffect(showContent ? 1 : 0.1)
                    .opacity(showContent ? 1 : 0)
            }

            VStack(spacing: OlasSpacing.sm) {
                Text("You're all set!")
                    .font(OlasFont.title1())
                    .foregroundStyle(Color.olasText1)

                Text("Your account is ready. Share your first photo!")
                    .font(OlasFont.body())
                    .foregroundStyle(Color.olasText2)
                    .multilineTextAlignment(.center)
            }
            .opacity(showContent ? 1 : 0)

            Spacer()

            VStack(spacing: OlasSpacing.sm) {
                // Primary CTA: open compose immediately after switching to the main UI.
                Button {
                    // Signal ContentView to open compose the moment it appears.
                    UserDefaults.standard.set(true, forKey: "openComposeOnNextLaunch")
                    // Coachmark not needed — compose is already opening.
                    firstPostCoachmarkSeen = true
                    hasCompletedOnboarding = true
                } label: {
                    HStack(spacing: OlasSpacing.xs) {
                        Image(systemName: "camera")
                            .font(.system(size: 16, weight: .semibold))
                        Text("Share your first photo")
                            .font(OlasFont.headline())
                    }
                    .foregroundStyle(Color.olasBackground)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(OlasPressedButtonStyle())

                // Secondary CTA: go to feed; coachmark will appear near the compose tab.
                Button {
                    hasCompletedOnboarding = true
                } label: {
                    Text("Look around first")
                        .font(OlasFont.subheadline())
                        .foregroundStyle(Color.olasText2)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(OlasPressedButtonStyle())
            }
            .opacity(showContent ? 1 : 0)
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.bottom, 48)
        }
        .background(Color.olasBackground)
        .onAppear {
            withAnimation(.linear(duration: 0.6)) {
                checkmarkProgress = 1.0
            }
            withAnimation(.olasStandard.delay(0.4)) {
                showContent = true
            }
        }
    }
}
