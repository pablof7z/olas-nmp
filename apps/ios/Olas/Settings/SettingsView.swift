import SwiftUI

struct SettingsView: View {
    @State private var showAdvanced = false

    var body: some View {
        // NMP-GAP(#31): Settings rows must come from Rust-owned capability config, not a hardcoded Swift array.
        List {
            // Tier 1
            Section {
                NavigationLink("Account") {
                    AccountSettingsView()
                }
                NavigationLink("Notifications") {
                    notificationsSettings
                }
                NavigationLink("Content & Filtering") {
                    WoTSettingsView()
                }
                NavigationLink("Appearance") {
                    appearanceSettings
                }
                NavigationLink("Help") {
                    helpSettings
                }
            }

            // Advanced (Tier 2)
            Section {
                DisclosureGroup("Advanced", isExpanded: $showAdvanced) {
                    NavigationLink("Servers") {
                        ServerSettingsView()
                    }
                    NavigationLink("Relays") {
                        RelaySettingsView()
                    }
                    NavigationLink("Wallet & Zaps") {
                        walletSettings
                    }
                    NavigationLink("Account Security") {
                        securitySettings
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
                Button("Log out") {}
                    .foregroundStyle(Color.olasDestructive)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Account")
    }
}
