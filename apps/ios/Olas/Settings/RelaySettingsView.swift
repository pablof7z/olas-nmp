import SwiftUI

@Observable @MainActor
final class RelaySettingsViewModel {
    var relays: [RelayEntry] = [
        RelayEntry(id: "damus", url: "wss://relay.damus.io", role: "both", isConnected: true, latencyMs: 42),
        RelayEntry(id: "nos", url: "wss://nos.lol", role: "both", isConnected: true, latencyMs: 87),
        RelayEntry(id: "primal", url: "wss://relay.primal.net", role: "both", isConnected: true, latencyMs: 55),
        RelayEntry(id: "purplepages", url: "wss://purplepag.es", role: "indexer", isConnected: true, latencyMs: 120),
    ]
    var newRelayURL = ""
    var isAdding = false
    var addError: String?

    func addRelay() {
        let url = newRelayURL.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty, url.hasPrefix("wss://") || url.hasPrefix("ws://") else {
            addError = "Enter a valid relay URL starting with wss://"
            return
        }
        guard !relays.contains(where: { $0.url == url }) else {
            addError = "This relay is already in your list."
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

    func changeRole(_ entry: RelayEntry, to role: String) {
        guard let idx = relays.firstIndex(where: { $0.id == entry.id }) else { return }
        NMPBridge.shared.removeRelay(url: entry.url)
        relays[idx].role = role
        NMPBridge.shared.addRelay(url: entry.url, role: role)
    }

    func resetToRecommended() {
        for relay in relays { NMPBridge.shared.removeRelay(url: relay.url) }
        relays = [
            RelayEntry(id: "damus", url: "wss://relay.damus.io", role: "both"),
            RelayEntry(id: "nos", url: "wss://nos.lol", role: "both"),
            RelayEntry(id: "primal", url: "wss://relay.primal.net", role: "both"),
            RelayEntry(id: "purplepages", url: "wss://purplepag.es", role: "indexer"),
        ]
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
                        TextField("wss://relay.example.com", text: $vm.newRelayURL)
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
                    Button("Add relay") { vm.isAdding = true }
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
        .navigationTitle("Relays")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func relayRow(_ relay: RelayEntry) -> some View {
        HStack(spacing: OlasSpacing.sm) {
            Circle()
                .fill(relay.isConnected ? Color.olasSuccess : Color.olasText3)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(URL(string: relay.url)?.host ?? relay.url)
                    .font(OlasFont.body())
                    .foregroundStyle(Color.olasText1)
                HStack(spacing: OlasSpacing.xs) {
                    Menu {
                        ForEach(["both", "read", "write", "indexer"], id: \.self) { role in
                            Button {
                                vm.changeRole(relay, to: role)
                            } label: {
                                HStack {
                                    Text(roleLabel(role))
                                    if relay.role == role {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 2) {
                            Text(roleLabel(relay.role))
                                .font(OlasFont.caption())
                                .foregroundStyle(Color.olasText3)
                            Image(systemName: "chevron.up.chevron.down")
                                .font(.system(size: 9, weight: .medium))
                                .foregroundStyle(Color.olasText3)
                        }
                    }
                    .buttonStyle(.plain)

                    if let ms = relay.latencyMs {
                        Text("· \(ms)ms")
                            .font(OlasFont.caption())
                            .foregroundStyle(latencyColor(ms))
                    }
                }
            }
        }
    }

    private func roleLabel(_ role: String) -> String {
        switch role {
        case "both": "Read & Write"
        case "read": "Read only"
        case "write": "Write only"
        case "indexer": "Indexer"
        default: role
        }
    }

    private func latencyColor(_ ms: Int) -> Color {
        if ms < 100 { return Color.olasSuccess }
        if ms < 300 { return Color.olasZap }
        return Color.olasDestructive
    }
}
