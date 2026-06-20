import SwiftUI

struct DiscoverView: View {
    // Populated from the NMPBridge profile cache — refreshes as profiles stream in.
    private var suggestedProfiles: [OlasProfile] {
        let wires = Array(NMPBridge.shared.profileCache.values)
        return wires
            .filter { $0.displayName != nil || $0.pictureUrl != nil }
            .prefix(20)
            .map { wire in
                OlasProfile(
                    pubkey: wire.pubkey,
                    name: wire.npubShort.isEmpty ? nil : wire.npubShort,
                    displayName: wire.displayName,
                    picture: wire.pictureUrl
                )
            }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OlasSpacing.xxl) {
                VStack(alignment: .leading, spacing: OlasSpacing.sm) {
                    Text("Suggested for you")
                        .font(OlasFont.headline())
                        .foregroundStyle(Color.olasText1)
                        .padding(.horizontal, OlasSpacing.md)

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
            .padding(.top, OlasSpacing.md)
            .padding(.bottom, 100)
        }
    }
}
