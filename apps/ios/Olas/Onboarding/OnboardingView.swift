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

    // Follow-pack discovery: the entire projection lives in Rust. Native re-reads
    // this snapshot on every kernel update frame (event-driven; no polling).
    var followPacks: FollowPacksSnapshot? = nil

    // Sign-in state
    var signInError: String? = nil
    var isSigningIn: Bool = false

    // P2-A: inbound invite — set when the app is launched via an invite link.
    // Display-only hint shown on WelcomeView; the pubkey is followed during
    // applySelectedPacks via a plain nmp.follow, alongside the pack selection.
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

    // MARK: - Follow-pack discovery (event-driven snapshot refresh)

    private var didSeedDefaults = false
    private var frameUpdateToken: Int? = nil
    private var lastFollowPacksJSON = ""

    func startPackDiscovery() {
        NMPBridge.shared.openFollowPackDiscovery()
        reloadFollowPacks()
        // Re-read the snapshot whenever the kernel pushes an update frame.
        // Event-driven only — no Timer, no poll loop.
        if frameUpdateToken == nil {
            frameUpdateToken = NMPBridge.shared.addFrameUpdateHandler { [weak self] in
                self?.reloadFollowPacks()
            }
        }
    }

    func stopPackDiscovery() {
        if let token = frameUpdateToken {
            NMPBridge.shared.removeFrameUpdateHandler(token)
            frameUpdateToken = nil
        }
        NMPBridge.shared.closeFollowPackDiscovery()
    }

    private func reloadFollowPacks() {
        guard let json = NMPBridge.shared.followPacksSnapshotJSON() else { return }
        guard json != lastFollowPacksJSON else { return }
        lastFollowPacksJSON = json
        guard let data = json.data(using: .utf8),
              let snapshot = try? JSONDecoder().decode(FollowPacksSnapshot.self, from: data)
        else { return }
        followPacks = snapshot
        // Pre-select default packs once, the first time they become available.
        if !didSeedDefaults, snapshot.state == "ready" {
            let defaults = snapshot.packs.filter { $0.defaultSelected }.map { $0.id }
            if !defaults.isEmpty {
                selectedPackIds.formUnion(defaults)
                didSeedDefaults = true
            }
        }
    }

    func togglePack(_ id: String) {
        if selectedPackIds.contains(id) {
            selectedPackIds.remove(id)
        } else {
            selectedPackIds.insert(id)
        }
    }

    /// Selection summary computed from the snapshot for footer display.
    var selectionSummary: (packs: Int, people: Int) {
        let selected = (followPacks?.packs ?? []).filter { selectedPackIds.contains($0.id) }
        return (selected.count, selected.reduce(0) { $0 + $1.memberCount })
    }

    /// Apply the selection: forward the opaque ids to Rust (which expands,
    /// unions, dedups, excludes self and dispatches one follow_many), then
    /// advance. The returned feed_default is informational — the kernel already
    /// flips the feed. P2-A: an inbound invite pubkey is followed separately.
    func applySelectedPacks() {
        let ids = Array(selectedPackIds)
        if !ids.isEmpty {
            _ = NMPBridge.shared.applySelectedFollowPacks(ids: ids)
        }
        if let invPk = inviterPubkey, !invPk.isEmpty {
            NMPBridge.shared.follow(pubkey: invPk)
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
