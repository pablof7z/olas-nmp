import SwiftUI

struct BlossomServer: Identifiable {
    let id: String
    var url: String
    var isPrimary: Bool
    var isMirror: Bool
    var status: ConnectionStatus
    var latencyMs: Int?
    var lastChecked: Date?

    enum ConnectionStatus {
        case connected, slow, down, unknown

        var label: String {
            switch self {
            case .connected: return "Connected"
            case .slow:      return "Slow"
            case .down:      return "Down"
            case .unknown:   return "Unknown"
            }
        }

        var color: Color {
            switch self {
            case .connected: return Color.olasSuccess
            case .slow:      return Color.olasZap
            case .down:      return Color.olasDestructive
            case .unknown:   return Color.olasText3
            }
        }
    }
}

@Observable @MainActor
final class ServerSettingsViewModel {
    var servers: [BlossomServer] = [
        BlossomServer(
            id: "primal",
            url: "https://blossom.primal.net",
            isPrimary: true,
            isMirror: false,
            status: .connected,
            latencyMs: 48,
            lastChecked: Date()
        )
    ]
    var newServerURL = ""
    var isAdding = false
    var addError: String?
    var mirrorAll = false

    func addServer() {
        let url = newServerURL.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty, url.hasPrefix("https://") || url.hasPrefix("http://") else {
            addError = "Enter a valid server URL starting with https://"
            return
        }
        guard !servers.contains(where: { $0.url == url }) else {
            addError = "This server is already in your list."
            return
        }
        let server = BlossomServer(id: UUID().uuidString, url: url, isPrimary: false,
                                  isMirror: mirrorAll, status: .unknown)
        servers.append(server)
        newServerURL = ""
        addError = nil
        isAdding = false
        // Connection test would be dispatched here
    }

    func removeServer(_ server: BlossomServer) {
        guard servers.count > 1 else { return } // Must keep at least one
        servers.removeAll { $0.id == server.id }
        if server.isPrimary, let first = servers.first {
            servers[0].isPrimary = true
            _ = first
        }
    }

    func setPrimary(_ server: BlossomServer) {
        for i in servers.indices { servers[i].isPrimary = false }
        if let idx = servers.firstIndex(where: { $0.id == server.id }) {
            servers[idx].isPrimary = true
            UserDefaults.standard.set(server.url, forKey: "primaryBlossomServer")
        }
    }
}

struct ServerSettingsView: View {
    @State private var vm = ServerSettingsViewModel()

    var body: some View {
        List {
            Section {
                ForEach(vm.servers) { server in
                    serverRow(server)
                }
                .onDelete { indexSet in
                    indexSet.forEach {
                        let s = vm.servers[$0]
                        if !s.isPrimary { vm.removeServer(s) }
                    }
                }
            }

            Section {
                Toggle("Mirror uploads to all servers", isOn: $vm.mirrorAll)
                    .foregroundStyle(Color.olasText1)
            }

            Section {
                if vm.isAdding {
                    VStack(alignment: .leading, spacing: OlasSpacing.xs) {
                        TextField("https://your-blossom-server.com", text: $vm.newServerURL)
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                            .autocapitalization(.none)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)

                        if let err = vm.addError {
                            Text(err)
                                .font(OlasFont.caption())
                                .foregroundStyle(Color.olasDestructive)
                        }

                        HStack {
                            Button("Cancel") { vm.isAdding = false; vm.addError = nil }
                                .foregroundStyle(Color.olasText2)
                            Spacer()
                            Button("Add") { vm.addServer() }
                                .foregroundStyle(Color.olasBlue)
                        }
                    }
                } else {
                    Button("Add server") { vm.isAdding = true }
                        .foregroundStyle(Color.olasBlue)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Servers")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func serverRow(_ server: BlossomServer) -> some View {
        HStack(spacing: OlasSpacing.sm) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: OlasSpacing.xs) {
                    Text(URL(string: server.url)?.host ?? server.url)
                        .font(OlasFont.body())
                        .foregroundStyle(Color.olasText1)
                        .lineLimit(1)

                    if server.isPrimary {
                        Text("PRIMARY")
                            .font(OlasFont.captionSmall())
                            .foregroundStyle(Color.olasBlue)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.olasBlue.opacity(0.15), in: Capsule())
                    }
                }

                HStack(spacing: OlasSpacing.xs) {
                    Circle().fill(server.status.color).frame(width: 6, height: 6)
                    Text(server.status.label)
                        .font(OlasFont.caption())
                        .foregroundStyle(server.status.color)
                    if let ms = server.latencyMs {
                        Text("· \(ms)ms")
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText3)
                    }
                }
            }

            Spacer()

            if !server.isPrimary {
                Button("Set primary") { vm.setPrimary(server) }
                    .font(OlasFont.caption())
                    .foregroundStyle(Color.olasBlue)
            }
        }
    }
}
