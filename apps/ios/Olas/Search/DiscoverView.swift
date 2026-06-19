import SwiftUI

struct DiscoverView: View {
    @State private var suggestedProfiles: [OlasProfile] = []
    @State private var isListening = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OlasSpacing.xxl) {
                // Suggested accounts
                VStack(alignment: .leading, spacing: OlasSpacing.sm) {
                    Text("Suggested for you")
                        .font(OlasFont.headline())
                        .foregroundStyle(Color.olasText1)
                        .padding(.horizontal, OlasSpacing.md)

                    if suggestedProfiles.isEmpty {
                        suggestedPlaceholders
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: OlasSpacing.sm) {
                                ForEach(suggestedProfiles, id: \.pubkey) { profile in
                                    SuggestedAccountCard(profile: profile)
                                }
                            }
                            .padding(.horizontal, OlasSpacing.md)
                        }
                    }
                }
            }
            .padding(.top, OlasSpacing.md)
            .padding(.bottom, 100)
        }
        .onAppear { startListening() }
    }

    private var suggestedPlaceholders: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: OlasSpacing.sm) {
                ForEach(0..<4, id: \.self) { i in
                    SuggestedAccountCard(profile: OlasProfile(
                        pubkey: "loading_\(i)",
                        name: "Loading...",
                        picture: nil
                    ))
                }
            }
            .padding(.horizontal, OlasSpacing.md)
        }
        .redacted(reason: .placeholder)
    }

    private func startListening() {
        guard !isListening else { return }
        isListening = true
        NMPBridge.shared.addEventHandler { json in
            Task { @MainActor in
                guard let profile = NMPBridge.shared.profile(from: json),
                      !suggestedProfiles.contains(where: { $0.pubkey == profile.pubkey }),
                      suggestedProfiles.count < 8
                else { return }
                suggestedProfiles.append(profile)
            }
        }
    }
}
