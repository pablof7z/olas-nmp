import SwiftUI

enum OnboardingStep: Hashable {
    case welcome
    case signIn
    case createAccount
    case followPacks
    case complete
}

@Observable @MainActor
final class OnboardingViewModel {
    var step: OnboardingStep = .welcome
    var createdName: String = ""
    var createdUsername: String = ""
    var selectedPackIds: Set<String> = []
    var mediaServerURL: String = "https://blossom.primal.net"

    // P0-A: follow-pack discovery (populated from kind:30000 event observer)
    var discoveredPacks: [FollowPackDescriptor] = []

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
        // Mark this as a fresh account so the first-post coachmark is eligible.
        // Sign-in paths (nsec/bunker) do NOT set this flag, preventing the coachmark
        // from appearing for existing users who already have posts.
        UserDefaults.standard.set(true, forKey: "coachmarkEligible")
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

    // MARK: - P0-A: follow-pack discovery

    private var packEventHandler: ((String) -> Void)?

    func startPackDiscovery() {
        NMPBridge.shared.openFollowPackDiscovery()
        // Register an event handler to decode kind:30000 events as they arrive.
        let handler: (String) -> Void = { [weak self] json in
            guard let self else { return }
            if let descriptor = NMPBridge.shared.decodeFollowPackEvent(json) {
                // Upsert by id (newer event replaces older for the same d-tag).
                if let idx = self.discoveredPacks.firstIndex(where: { $0.id == descriptor.id }) {
                    self.discoveredPacks[idx] = descriptor
                } else {
                    self.discoveredPacks.append(descriptor)
                }
            }
        }
        packEventHandler = handler
        NMPBridge.shared.addEventHandler(handler)
    }

    func stopPackDiscovery() {
        NMPBridge.shared.closeFollowPackDiscovery()
    }

    /// Apply the selected packs: collect all pubkeys from selected descriptors,
    /// deduplicate in Rust, dispatch nmp.follow for each, then advance.
    func applySelectedPacks() {
        guard !selectedPackIds.isEmpty else {
            advance(to: .complete)
            return
        }
        let allPubkeys = discoveredPacks
            .filter { selectedPackIds.contains($0.id) }
            .flatMap { $0.pubkeys }
        let result = NMPBridge.shared.applyFollowPackPubkeys(allPubkeys)
        // If Rust says feed_default is "following", flip the feed mode.
        if result?.feedDefault == "following" {
            NMPBridge.shared.setFeedMode("following")
        }
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
