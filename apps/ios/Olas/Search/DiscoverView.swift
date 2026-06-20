import SwiftUI

// MARK: - Models

struct DiscoverProfile: Decodable {
    let pubkey: String
    let mutualCount: Int

    enum CodingKeys: String, CodingKey {
        case pubkey
        case mutualCount = "mutual_count"
    }
}

struct DiscoverSection: Decodable {
    let title: String
    let reason: String
    let profiles: [DiscoverProfile]
}

// MARK: - DiscoverView

struct DiscoverView: View {
    @State private var sections: [DiscoverSection] = []
    @State private var isLoading = true

    private var bridge: NMPBridge { NMPBridge.shared }
    private let consumer = "olas.discover"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OlasSpacing.xxl) {
                if isLoading {
                    loadingPlaceholders
                } else if !hasRealContent {
                    emptyState
                } else {
                    ForEach(sections.filter { !$0.profiles.isEmpty && $0.reason != "graph_empty" },
                            id: \.title) { section in
                        sectionView(section)
                    }
                }
            }
            .padding(.top, OlasSpacing.md)
            .padding(.bottom, 100)
        }
        .onAppear {
            loadSections()
        }
        .onChange(of: bridge.activeAccountPubkey) { _, _ in
            releaseClaimedProfiles()
            loadSections()
        }
        .onDisappear {
            releaseClaimedProfiles()
        }
    }

    private var hasRealContent: Bool {
        sections.contains { $0.reason != "graph_empty" && !$0.profiles.isEmpty }
    }

    private func loadSections() {
        guard let activePubkey = bridge.activeAccountPubkey, !activePubkey.isEmpty else {
            isLoading = false
            return
        }
        guard let json = bridge.discoverSectionsJSON(activePubkey: activePubkey),
              let data = json.data(using: .utf8),
              let decoded = try? JSONDecoder().decode([DiscoverSection].self, from: data) else {
            isLoading = false
            return
        }
        sections = decoded
        isLoading = false
        // Claim profiles for all ranked pubkeys so the kernel fetches their kind:0 events.
        for section in decoded {
            for dp in section.profiles.prefix(15) {
                bridge.claimProfile(pubkey: dp.pubkey, consumer: consumer)
            }
        }
    }

    private func releaseClaimedProfiles() {
        for section in sections {
            for dp in section.profiles.prefix(15) {
                bridge.releaseProfile(pubkey: dp.pubkey, consumer: consumer)
            }
        }
    }

    @ViewBuilder
    private func sectionView(_ section: DiscoverSection) -> some View {
        VStack(alignment: .leading, spacing: OlasSpacing.sm) {
            Text(section.title)
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasText1)
                .padding(.horizontal, OlasSpacing.md)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: OlasSpacing.sm) {
                    ForEach(section.profiles.prefix(15), id: \.pubkey) { dp in
                        cardForProfile(dp)
                    }
                }
                .padding(.horizontal, OlasSpacing.md)
            }
        }
    }

    @ViewBuilder
    private func cardForProfile(_ dp: DiscoverProfile) -> some View {
        let wire = bridge.profile(forPubkey: dp.pubkey)
        let profile = OlasProfile(
            pubkey: dp.pubkey,
            name: wire?.npubShort.isEmpty == false ? wire?.npubShort : nil,
            displayName: wire?.displayName,
            picture: wire?.pictureUrl
        )
        let proof = SocialProof(
            mutualFollowers: [],
            mutualCount: dp.mutualCount,
            reasonKind: dp.mutualCount > 0 ? "followed_by_mutuals" : "new_account"
        )
        SuggestedAccountCard(profile: profile, socialProof: proof)
    }

    private var loadingPlaceholders: some View {
        VStack(alignment: .leading, spacing: OlasSpacing.sm) {
            Rectangle()
                .fill(Color.olasSurface2)
                .frame(width: 160, height: 20)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .padding(.horizontal, OlasSpacing.md)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: OlasSpacing.sm) {
                    ForEach(0..<4, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.olasSurface2)
                            .frame(width: 120, height: 170)
                    }
                }
                .padding(.horizontal, OlasSpacing.md)
            }
        }
    }

    private var emptyState: some View {
        Text("Follow some accounts to see suggestions here.")
            .font(OlasFont.body())
            .foregroundStyle(Color.olasText2)
            .multilineTextAlignment(.center)
            .padding(.horizontal, OlasSpacing.xl)
            .frame(maxWidth: .infinity)
            .padding(.top, OlasSpacing.xxl)
    }
}
