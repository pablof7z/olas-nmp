import SwiftUI

struct SettingsView: View {
    @State private var showAdvanced = false

    var body: some View {
        List {
            Section {
                NavigationLink("Content & Filtering") {
                    WoTSettingsView()
                }
                NavigationLink("Help") {
                    helpSettings
                }
            }

            Section {
                DisclosureGroup("Advanced", isExpanded: $showAdvanced) {
                    NavigationLink("Servers") {
                        ServerSettingsView()
                    }
                    NavigationLink("Relays") {
                        RelaySettingsView()
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
}
