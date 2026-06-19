import SwiftUI

@Observable @MainActor
final class RelaySettingsViewModel {
    var relays: [RelayEntry]
    var newRelayURL = ""
    var isAdding = false
    var addError: String?

    init() {
        relays = NMPBridge.shared.defaultRelays()
    }

    func addRelay() {
        let url = newRelayURL.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty, url.hasPrefix("wss://") || url.hasPrefix("ws://") else {
            addError = "Enter a valid server URL starting with wss://"
            return
        }
        guard !relays.contains(where: { $0.url == url }) else {
            addError = "This server is already in your list."
            return
        }
        let entry = RelayEntry(id: UUID().uuidString, url: url, role: "both")
        relays.append(entry)
        NMPBridge.shared.addRelay(url: url, role: "both")
        newRelayURL = ""
        addError = nil
        isAdding = false
    }

    func removeRelay(_ entry: RelayEntry) {
        relays.removeAll { $0.id == entry.id }
        NMPBridge.shared.removeRelay(url: entry.url)
    }

    func resetToRecommended() {
        for relay in relays { NMPBridge.shared.removeRelay(url: relay.url) }
        relays = NMPBridge.shared.defaultRelays()
        for relay in relays {
            NMPBridge.shared.addRelay(url: relay.url, role: relay.role)
        }
    }
}

struct RelaySettingsView: View {
    @State private var vm = RelaySettingsViewModel()

    var body: some View {
        List {
            Section {
                ForEach(vm.relays) { relay in
                    relayRow(relay)
                }
                .onDelete { indexSet in
                    indexSet.forEach { vm.removeRelay(vm.relays[$0]) }
                }
            }

            Section {
                if vm.isAdding {
                    HStack {
                        TextField("Server URL", text: $vm.newRelayURL)
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                            .autocapitalization(.none)
                            .autocorrectionDisabled()
                        Button("Add") { vm.addRelay() }
                            .foregroundStyle(Color.olasBlue)
                    }
                    if let err = vm.addError {
                        Text(err)
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasDestructive)
                    }
                } else {
                    Button("Add server") { vm.isAdding = true }
                        .foregroundStyle(Color.olasBlue)
                }
            }

            Section {
                Button("Reset to recommended") { vm.resetToRecommended() }
                    .foregroundStyle(Color.olasText2)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Network Servers")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func relayRow(_ relay: RelayEntry) -> some View {
        HStack(spacing: OlasSpacing.sm) {
            Circle()
                .fill(relay.isConnected ? Color.olasSuccess : Color.olasText3)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(displayName(for: relay.url))
                    .font(OlasFont.body())
                    .foregroundStyle(Color.olasText1)
                HStack(spacing: OlasSpacing.xs) {
                    Text(roleLabel(relay.role))
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText3)
                    if let ms = relay.latencyMs {
                        Text("· \(ms)ms")
                            .font(OlasFont.caption())
                            .foregroundStyle(latencyColor(ms))
                    }
                }
            }
        }
    }

    private func latencyColor(_ ms: Int) -> Color {
        if ms < 100 { return Color.olasSuccess }
        if ms < 300 { return Color.olasZap }
        return Color.olasDestructive
    }

    private func displayName(for url: String) -> String {
        let host = URL(string: url)?.host ?? url
        return host.replacingOccurrences(of: "relay.", with: "")
    }

    private func roleLabel(_ role: String) -> String {
        role == "both" || role == "read-write" ? "Read + post" : role
    }
}
