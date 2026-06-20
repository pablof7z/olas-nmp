import SwiftUI

// MARK: - Photo Lift coordinator

/// Shared state owned by ContentView. Any tab can open the fullscreen viewer
/// by calling open(post:index:context:); ContentView renders the overlay.
@Observable
@MainActor
final class PhotoLiftState {
    var item: ZoomItem? = nil

    struct ZoomItem: Identifiable {
        /// Stable ID — must match the source view's matchedGeometryEffect id.
        let id: String
        let post: PhotoPost
        let index: Int
    }

    func open(post: PhotoPost, index: Int, context: String) {
        item = ZoomItem(id: "\(context)-\(post.id)-\(index)", post: post, index: index)
    }

    func close() { item = nil }
}

// MARK: - Zoom namespace environment key

struct ZoomNamespaceKey: EnvironmentKey {
    /// nil default — source views skip matchedGeometryEffect when not injected.
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {
    var zoomNamespace: Namespace.ID? {
        get { self[ZoomNamespaceKey.self] }
        set { self[ZoomNamespaceKey.self] = newValue }
    }
}

// MARK: - ContentView

struct ContentView: View {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @AppStorage("firstPostCoachmarkSeen") private var firstPostCoachmarkSeen = false
    /// True only when the account was freshly created (not signed in via nsec/bunker).
    /// Set in OnboardingViewModel.createAndContinue(); sign-in paths never set it.
    @AppStorage("coachmarkEligible") private var coachmarkEligible = false
    @State private var selectedTab: Int = 0
    @State private var prevTab: Int = 0
    @State private var showCompose: Bool = false
    @State private var photoLift = PhotoLiftState()
    @Namespace private var zoomNamespace
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let queue = UploadQueue.shared

    var body: some View {
        if !hasCompletedOnboarding {
            OnboardingView()
        } else {
            mainTabs
                .onAppear { consumeOpenComposeIntent() }
        }
    }

    // MARK: - Main tab interface

    private var mainTabs: some View {
        // Native TabView — iOS 26 automatically applies liquid glass to the tab bar.
        // Use .tabItem/.tag (iOS 17+) rather than Tab(value:) which requires iOS 18+.
        TabView(selection: $selectedTab) {
            FeedView()
                .tabItem { Label("Home", systemImage: "house") }
                .tag(0)

            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(1)

            // Tag 2 is a compose trigger: immediately revert selection and open sheet.
            Color.clear
                .tabItem { Label("Post", systemImage: "plus") }
                .tag(2)

            NotificationsView()
                .tabItem { Label("Notifications", systemImage: "bell") }
                .tag(3)

            ProfileView(pubkey: nil, isOwn: true)
                .tabItem { Label("Profile", systemImage: "person.circle") }
                .tag(4)
        }
        .onChange(of: selectedTab) { _, newValue in
            if newValue == 2 {
                selectedTab = prevTab
                showCompose = true
            } else {
                prevTab = newValue
            }
        }
        .animation(.olasStandard, value: queue.active != nil)
        .sheet(isPresented: $showCompose) {
            ComposeNavigator()
                .presentationDetents([.large])
                .presentationDragIndicator(.hidden)
        }
        .overlay(alignment: .bottom) {
            if let active = queue.active {
                UploadMiniPlayer(upload: active)
                    .padding(.bottom, 83 + 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .overlay(alignment: .bottom) {
            if !firstPostCoachmarkSeen && coachmarkEligible {
                FirstPostCoachmark(onDismiss: { firstPostCoachmarkSeen = true }) {
                    firstPostCoachmarkSeen = true
                    showCompose = true
                }
                // Sit above the tab bar (83 pt) with a small gap.
                .padding(.bottom, 98)
                .padding(.horizontal, OlasSpacing.xl)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.olasStandard, value: firstPostCoachmarkSeen)
        // Zoom overlay: covers tab bar + nav bar. matchedGeometryEffect animates
        // the image from its source slot to fullscreen and back on dismiss.
        .overlay {
            if let item = photoLift.item {
                FullscreenImageView(
                    post: item.post,
                    initialIndex: item.index,
                    namespace: reduceMotion ? nil : zoomNamespace,
                    sourceId: item.id,
                    onDismiss: {
                        withAnimation(.olasStandard) { photoLift.item = nil }
                    }
                )
                // Reduce Motion: plain crossfade; otherwise identity (matched geometry
                // handles the zoom animation implicitly).
                .transition(reduceMotion ? .opacity : .identity)
                .zIndex(100)
            }
        }
        // Inject state and namespace so any tab can trigger the viewer.
        .environment(photoLift)
        .environment(\.zoomNamespace, zoomNamespace)
    }

    // MARK: - Open-compose intent

    /// Consumes the flag written by OnboardingCompleteView when the user chose
    /// "Share your first photo". Safe to call multiple times — clears itself.
    private func consumeOpenComposeIntent() {
        if UserDefaults.standard.bool(forKey: "openComposeOnNextLaunch") {
            UserDefaults.standard.removeObject(forKey: "openComposeOnNextLaunch")
            showCompose = true
        }
    }
}

// MARK: - First-post coachmark

struct FirstPostCoachmark: View {
    let onDismiss: () -> Void
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: OlasSpacing.md) {
            Image(systemName: "camera.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.olasText1)

            VStack(alignment: .leading, spacing: 2) {
                Text("Share your first photo")
                    .font(OlasFont.subheadline())
                    .foregroundStyle(Color.olasText1)
                Text("Tap + to post your first photo.")
                    .font(OlasFont.caption())
                    .foregroundStyle(Color.olasText2)
            }

            Spacer()

            Button {
                onDismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.olasText3)
                    .padding(OlasSpacing.xs)
            }
        }
        .padding(OlasSpacing.md)
        .background(Color.olasSurface2, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.olasBorder, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.4), radius: 12, y: 4)
        .onTapGesture { onTap() }
    }
}
