import SwiftUI

struct ProfileWire: Equatable {
    let pubkey: String
    let displayName: String?
    let about: String?
    let pictureUrl: String?
    let nip05: String?
    let npub: String
    let npubShort: String

    var display: String {
        if let displayName, !displayName.isEmpty { return displayName }
        if !npubShort.isEmpty { return npubShort }
        return String(pubkey.prefix(8))
    }
}

@MainActor
protocol NostrProfileHost: AnyObject, Sendable {
    func profile(forPubkey pubkey: String) -> ProfileWire?
    func claimProfile(pubkey: String, consumerID: String)
    func releaseProfile(pubkey: String, consumerID: String)
}

private struct NostrProfileHostKey: EnvironmentKey {
    static let defaultValue: NostrProfileHost? = nil
}

extension EnvironmentValues {
    var nostrProfileHost: NostrProfileHost? {
        get { self[NostrProfileHostKey.self] }
        set { self[NostrProfileHostKey.self] = newValue }
    }
}

struct NostrAvatar: View {
    let pubkey: String
    let pictureUrl: URL?
    let size: CGFloat

    @Environment(\.nostrProfileHost) private var profileHost
    @State private var bridge = NMPBridge.shared
    @State private var consumerID = "olas.avatar.\(UUID().uuidString)"

    private var resolvedURL: URL? {
        pictureUrl ?? profileWire?.pictureUrl.flatMap(URL.init(string:))
    }

    private var profileWire: ProfileWire? {
        profileHost?.profile(forPubkey: pubkey) ?? bridge.profile(forPubkey: pubkey)
    }

    private var initials: String {
        let display = profileWire?.display ?? String(pubkey.prefix(8))
        return String(display.prefix(2)).uppercased()
    }

    var body: some View {
        ZStack {
            Circle().fill(Color.olasSurface2)
            if let resolvedURL {
                AsyncImage(url: resolvedURL) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .onAppear {
            (profileHost ?? bridge).claimProfile(pubkey: pubkey, consumerID: consumerID)
        }
        .onDisappear {
            (profileHost ?? bridge).releaseProfile(pubkey: pubkey, consumerID: consumerID)
        }
    }

    private var placeholder: some View {
        Text(initials)
            .font(.system(size: max(11, size * 0.35), weight: .semibold))
            .foregroundStyle(Color.olasText2)
            .frame(width: size, height: size)
    }
}

struct NostrProfileName: View {
    let pubkey: String
    let font: Font
    let color: Color

    @Environment(\.nostrProfileHost) private var profileHost
    @State private var bridge = NMPBridge.shared
    @State private var consumerID = "olas.name.\(UUID().uuidString)"

    var body: some View {
        Text((profileHost?.profile(forPubkey: pubkey) ?? bridge.profile(forPubkey: pubkey))?.display ?? String(pubkey.prefix(8)))
            .font(font)
            .foregroundStyle(color)
            .lineLimit(1)
            .onAppear {
                (profileHost ?? bridge).claimProfile(pubkey: pubkey, consumerID: consumerID)
            }
            .onDisappear {
                (profileHost ?? bridge).releaseProfile(pubkey: pubkey, consumerID: consumerID)
            }
    }
}

struct NostrSignerOption: Identifiable {
    let id: String
    let name: String
    let scheme: String
}

struct NostrLoginBlock: View {
    let onSignerSelected: (NostrSignerOption) -> Void
    let onManualKey: () -> Void

    private let signers = [
        NostrSignerOption(id: "amber", name: "Amber", scheme: "nostrsigner"),
        NostrSignerOption(id: "nostrconnect", name: "Nostr Connect", scheme: "nostrconnect"),
    ]

    var body: some View {
        VStack(spacing: OlasSpacing.md) {
            ForEach(signers) { signer in
                Button {
                    onSignerSelected(signer)
                } label: {
                    HStack {
                        Text(signer.name)
                            .font(OlasFont.body())
                            .foregroundStyle(Color.olasText1)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.olasText3)
                    }
                    .padding(.horizontal, OlasSpacing.md)
                    .frame(height: 52)
                    .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }

            Button(action: onManualKey) {
                Text("Use private key")
                    .font(OlasFont.body())
                    .foregroundStyle(Color.olasText2)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.olasBorder, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }
}
