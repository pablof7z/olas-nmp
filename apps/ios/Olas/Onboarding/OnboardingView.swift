import SwiftUI

enum OnboardingStep: Hashable {
    case welcome
    case signIn
    case createAccount
    case followPacks
    case mediaServer
    case complete
}

@Observable @MainActor
final class OnboardingViewModel {
    var step: OnboardingStep = .welcome
    var createdName: String = ""
    var createdUsername: String = ""
    var selectedPackIds: Set<String> = []
    var mediaServerURL: String = "https://blossom.primal.net"

    // Sign-in state
    var signInError: String? = nil
    var isSigningIn: Bool = false

    func advance(to next: OnboardingStep) {
        withAnimation(.olasStandard) {
            step = next
        }
    }

    func createAndContinue() {
        NMPBridge.shared.createAccount(name: createdName, username: createdUsername)
        advance(to: .followPacks)
    }

    func signInNsec(_ nsec: String) {
        guard !isSigningIn else { return }
        isSigningIn = true
        signInError = nil
        // NMPBridge.signInNsec is fire-and-forget (sync JNI passthrough).
        NMPBridge.shared.signInNsec(nsec)
        isSigningIn = false
        advance(to: .complete)
    }

    func signInBunker(_ uri: String) {
        guard !isSigningIn else { return }
        isSigningIn = true
        signInError = nil
        // NMPBridge.signInBunker is fire-and-forget (sync JNI passthrough).
        NMPBridge.shared.signInBunker(uri)
        isSigningIn = false
        advance(to: .complete)
    }
}

struct OnboardingView: View {
    @State private var vm = OnboardingViewModel()

    var body: some View {
        ZStack {
            switch vm.step {
            case .welcome:
                WelcomeView(vm: vm)
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
                    .id(OnboardingStep.welcome)

            case .signIn:
                SignInView(vm: vm)
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
                    .id(OnboardingStep.signIn)

            case .createAccount:
                CreateAccountView(vm: vm)
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
                    .id(OnboardingStep.createAccount)

            case .followPacks:
                FollowPacksView(vm: vm)
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
                    .id(OnboardingStep.followPacks)

            case .mediaServer:
                MediaServerView(vm: vm)
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
                    .id(OnboardingStep.mediaServer)

            case .complete:
                OnboardingCompleteView()
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)
                    ))
                    .id(OnboardingStep.complete)
            }
        }
        .background(Color.olasBackground)
        .preferredColorScheme(.dark)
        .animation(.olasStandard, value: vm.step)
    }
}
