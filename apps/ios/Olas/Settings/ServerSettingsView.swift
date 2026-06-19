import SwiftUI

struct BlossomServerOption: Identifiable {
    let id: String
    let name: String
    let description: String
    let url: String
    let isRecommended: Bool
}

struct ServerSettingsView: View {
    @AppStorage("primaryBlossomServer") private var primaryServerURL = "https://blossom.primal.net"

    private let servers: [BlossomServerOption] = [
        BlossomServerOption(
            id: "primal",
            name: "Olas Network",
            description: "Primal's Blossom infrastructure.",
            url: "https://blossom.primal.net",
            isRecommended: true
        ),
        BlossomServerOption(
            id: "satellite",
            name: "Satellite.earth",
            description: "Community-run media hosting.",
            url: "https://cdn.satellite.earth",
            isRecommended: false
        ),
        BlossomServerOption(
            id: "nostrcheck",
            name: "Nostrcheck",
            description: "Privacy-focused media hosting.",
            url: "https://nostrcheck.me",
            isRecommended: false
        )
    ]

    var body: some View {
        List {
            Section {
                ForEach(servers) { server in
                    serverRow(server)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.olasBackground)
        .navigationTitle("Servers")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func serverRow(_ server: BlossomServerOption) -> some View {
        Button {
            primaryServerURL = server.url
        } label: {
            HStack(spacing: OlasSpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: OlasSpacing.xs) {
                        Text(server.name)
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                        if server.isRecommended {
                            Text("RECOMMENDED")
                                .font(OlasFont.captionSmall())
                                .foregroundStyle(Color.olasBlue)
                        }
                    }
                    Text(server.description)
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText2)
                    Text(server.url)
                        .font(OlasFont.captionSmall())
                        .foregroundStyle(Color.olasText3)
                }
                Spacer()
                Image(systemName: primaryServerURL == server.url ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(primaryServerURL == server.url ? Color.olasBlue : Color.olasText3)
            }
        }
        .buttonStyle(.plain)
    }
}
