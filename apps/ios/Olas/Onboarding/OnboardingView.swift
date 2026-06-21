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

    // P2-A: inbound invite — set when the app is launched via an invite link.
    // Display-only hint shown on WelcomeView; the pubkey is pre-seeded as a
    // follow during applySelectedPacks (reuses the P0-A olas_apply_follow_pack_pubkeys path).
    var inviterPubkey: String? = nil
    var inviterDisplayHint: String? = nil

    // P2-A: consume any pending invite token written by OlasApp.onOpenURL.
    // Called once during view initialisation; clears the stored token so it is
    // not re-consumed on the next launch.
    func consumePendingInvite() {
        guard inviterPubkey == nil,
              let token = UserDefaults.standard.string(forKey: "pendingInviteToken"),
              !token.isEmpty
        else { return }
        UserDefaults.standard.removeObject(forKey: "pendingInviteToken")
        if let resolved = NMPBridge.shared.resolveInvite(token: token) {
            inviterPubkey = resolved.pubkey
            inviterDisplayHint = resolved.hint
        }
    }

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
    /// P2-A: also pre-seeds the inviter pubkey (if an invite was used) into
    /// the same follow batch — reuses olas_apply_follow_pack_pubkeys so there
    /// is exactly one kind:3 write for the entire set (no race with pack pubkeys).
    func applySelectedPacks() {
        var allPubkeys = discoveredPacks
            .filter { selectedPackIds.contains($0.id) }
            .flatMap { $0.pubkeys }
        // Pre-seed inviter into the follow batch (dedup happens in Rust).
        if let invPk = inviterPubkey, !invPk.isEmpty {
            allPubkeys.append(invPk)
        }
        guard !allPubkeys.isEmpty else {
            advance(to: .complete)
            return
        }
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
