import SwiftUI

struct ContentView: View {
    @State private var selectedTab: Int = 0
    @State private var prevTab: Int = 0
    @State private var showCompose: Bool = false
    private let queue = UploadQueue.shared

    var body: some View {
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
                .tabItem { Label("Activity", systemImage: "bell") }
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
        }
        .overlay(alignment: .bottom) {
            if let active = queue.active {
                UploadMiniPlayer(upload: active)
                    .padding(.bottom, 83 + 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }
}
