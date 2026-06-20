import SwiftUI

@main
struct OlasApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup(content: {
            ContentView()
                .preferredColorScheme(.dark)
                .environment(\.nostrProfileHost, NMPBridge.shared)
                .task {
                    await NMPBridge.shared.initialize()
                    #if DEBUG
                    // Auto-sign-in for testing: write nsec to debug_nsec.txt in Documents.
                    if NMPBridge.shared.activeAccountPubkey == nil,
                       let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                        let nsecFile = docs.appendingPathComponent("debug_nsec.txt")
                        if let nsec = try? String(contentsOf: nsecFile, encoding: .utf8)
                            .trimmingCharacters(in: .whitespacesAndNewlines), !nsec.isEmpty {
                            NMPBridge.shared.signInNsec(nsec)
                        }
                    }
                    #endif
                }
        })
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                Task<Void, Never> { await NMPBridge.shared.appDidBecomeActive() }
            case .background:
                Task<Void, Never> { await NMPBridge.shared.appDidEnterBackground() }
            default:
                break
            }
        }
    }
}
