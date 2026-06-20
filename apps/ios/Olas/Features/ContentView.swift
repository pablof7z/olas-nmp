import SwiftUI

struct ContentView: View {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @AppStorage("firstPostCoachmarkSeen") private var firstPostCoachmarkSeen = false
    @State private var selectedTab: Int = 0
    @State private var prevTab: Int = 0
    @State private var showCompose: Bool = false
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
            if !firstPostCoachmarkSeen {
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
