import SwiftUI

struct SettingsView: View {
    @State private var showAdvanced = false

    private struct SettingItem: Decodable { let id: String; let label: String }

    private var settingsItems: [SettingItem] {
        guard let catalogJSON = NMPBridge.shared.settingsCatalogJSON(),
              let data = catalogJSON.data(using: .utf8),
              let items = try? JSONDecoder().decode([SettingItem].self, from: data) else {
            return []
        }
        return items
    }

    @ViewBuilder
    private func destination(for id: String) -> some View {
        switch id {
        case "account":       AccountSettingsView()
        case "notifications": notificationsSettings
        case "content":       WoTSettingsView()
        case "appearance":    appearanceSettings
        case "help":          helpSettings
        case "servers":       ServerSettingsView()
        case "relays":        RelaySettingsView()
        case "wallet":        walletSettings
        case "security":      securitySettings
        default:              Text(id).foregroundStyle(Color.olasText1)
        }
    }

    var body: some View {
        let items = settingsItems
        let tier1Ids = ["account", "notifications", "content", "appearance", "help"]
        let tier2Ids = ["servers", "relays", "wallet", "security"]
        let tier1 = items.filter { tier1Ids.contains($0.id) }
        let tier2 = items.filter { tier2Ids.contains($0.id) }
        let fallbackTier1: [SettingItem] = [
            .init(id: "account", label: "Account"),
            .init(id: "notifications", label: "Notifications"),
            .init(id: "content", label: "Content & Filtering"),
            .init(id: "appearance", label: "Appearance"),
            .init(id: "help", label: "Help")
        ]
        let fallbackTier2: [SettingItem] = [
            .init(id: "servers", label: "Servers"),
            .init(id: "relays", label: "Relays"),
            .init(id: "wallet", label: "Wallet & Zaps"),
            .init(id: "security", label: "Account Security")
        ]
        let displayTier1 = tier1.isEmpty ? fallbackTier1 : tier1
        let displayTier2 = tier2.isEmpty ? fallbackTier2 : tier2

        return List {
            Section {
                ForEach(displayTier1, id: \.id) { item in
                    NavigationLink(item.label) { destination(for: item.id) }
                }
            }
            Section {
                DisclosureGroup("Advanced", isExpanded: $showAdvanced) {
                    ForEach(displayTier2, id: \.id) { item in
                        NavigationLink(item.label) { destination(for: item.id) }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .preferredColorScheme(.dark)
    }

    private var notificationsSettings: some View {
        List {
            Section("Types") {
                Toggle("Reactions", isOn: .constant(true))
                Toggle("Comments", isOn: .constant(true))
                Toggle("Zaps", isOn: .constant(true))
                Toggle("New Followers", isOn: .constant(true))
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Notifications")
    }

    private var appearanceSettings: some View {
        List {
            Section("Video") {
                Picker("Autoplay", selection: .constant("wifi")) {
                    Text("Always").tag("always")
                    Text("Wi-Fi only").tag("wifi")
                    Text("Never").tag("never")
                }
            }
            Section("Data") {
                Toggle("Data Saver", isOn: .constant(false))
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Appearance")
    }

    private var helpSettings: some View {
        List {
            Section {
                Link("About Olas", destination: URL(string: "https://olas.app")!)
                Link("Support", destination: URL(string: "https://olas.app/support")!)
                Link("Privacy Policy", destination: URL(string: "https://olas.app/privacy")!)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Help")
    }

    private var walletSettings: some View {
        List {
            Section("NWC Connection") {
                Button("Connect Wallet") {
                    // Open wallet connect flow
                }
                .foregroundStyle(Color.olasBlue)
            }
            Section("Default Zap Amount") {
                Picker("Amount", selection: .constant(21)) {
                    Text("21 sats").tag(21)
                    Text("100 sats").tag(100)
                    Text("500 sats").tag(500)
                    Text("1000 sats").tag(1000)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Wallet & Zaps")
    }

    private var securitySettings: some View {
        List {
            Section {
                Button("Export Recovery Key") {
                    // Key export flow
                }
                .foregroundStyle(Color.olasDestructive)
                Button("Backup to Keychain") {}
                    .foregroundStyle(Color.olasBlue)
            }
            Section("Signer Type") {
                Picker("Signer", selection: .constant("local")) {
                    Text("Local key").tag("local")
                    Text("NIP-46 Bunker").tag("nip46")
                    Text("NIP-55 App").tag("nip55")
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Account Security")
    }
}

// MARK: - Account Settings

struct AccountSettingsView: View {
    @State private var showLogoutConfirm = false

    var body: some View {
        List {
            Section {
                NavigationLink("Edit Profile") {
                    EditProfileView(profile: OlasProfile(pubkey: ""))
                }
                NavigationLink("NIP-05 Verification") {
                    Text("NIP-05").foregroundStyle(Color.olasText1)
                }
            }
            Section {
                Button("Log out") { showLogoutConfirm = true }
                    .foregroundStyle(Color.olasDestructive)
            }
        }
        .confirmationDialog("Log out of Olas?", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("Log out", role: .destructive) {
                NMPBridge.shared.signOut()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You'll need your Nostr key to sign back in.")
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Account")
    }
}
