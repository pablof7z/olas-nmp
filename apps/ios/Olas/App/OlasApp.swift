import SwiftUI

@main
struct OlasApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
                .task {
                    await NMPBridge.shared.initialize()
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                Task { @MainActor in NMPBridge.shared.appDidBecomeActive() }
            case .background:
                Task { @MainActor in NMPBridge.shared.appDidEnterBackground() }
            default:
                break
            }
        }
    }
}
