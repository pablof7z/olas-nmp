import Foundation
import SwiftUI

struct ProfileWire: Codable, Equatable, Sendable {
    let pubkey: String
    let displayName: String?
    let about: String?
    let pictureUrl: String?
    let nip05: String?
    let npub: String
    let npubShort: String

    var display: String {
        if let displayName, !displayName.isEmpty { return displayName }
        return npubShort
    }

    var avatarURL: URL? {
        guard let pictureUrl, !pictureUrl.isEmpty else { return nil }
        return URL(string: pictureUrl)
    }
}

@MainActor
protocol NostrProfileHost: AnyObject {
    func profile(forPubkey pubkey: String) -> ProfileWire?
    func claimProfile(pubkey: String, consumerID: String)
    func releaseProfile(pubkey: String, consumerID: String)
}

private struct NostrProfileHostKey: EnvironmentKey {
    nonisolated(unsafe) static let defaultValue: NostrProfileHost? = nil
}

extension EnvironmentValues {
    var nostrProfileHost: NostrProfileHost? {
        get { self[NostrProfileHostKey.self] }
        set { self[NostrProfileHostKey.self] = newValue }
    }
}

struct NostrAvatar: View {
    @Environment(\.nostrProfileHost) private var profileHost

    let pubkey: String
    let pictureUrl: URL?
    let size: CGFloat

    @State private var consumerID = "olas-avatar.\(UUID().uuidString)"
    @State private var claimedPubkey: String?

    init(pubkey: String, pictureUrl: URL? = nil, size: CGFloat = 40) {
        self.pubkey = pubkey
        self.pictureUrl = pictureUrl
        self.size = size
    }

    var body: some View {
        let url = pictureUrl ?? profileHost?.profile(forPubkey: pubkey)?.avatarURL

        Group {
            if let url {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        fallback
                    }
                }
            } else {
                fallback
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .task(id: pubkey) { claim(pubkey) }
        .onDisappear { releaseClaim() }
    }

    private var fallback: some View {
        ZStack {
            Circle().fill(NostrIdenticon.color(forPubkey: pubkey))
            Text(NostrIdenticon.initial(forPubkey: pubkey))
                .font(.system(size: size * 0.35, weight: .semibold))
                .foregroundStyle(.white)
        }
    }

    private func claim(_ pubkey: String) {
        if let claimedPubkey, claimedPubkey != pubkey {
            profileHost?.releaseProfile(pubkey: claimedPubkey, consumerID: consumerID)
        }
        claimedPubkey = pubkey
        profileHost?.claimProfile(pubkey: pubkey, consumerID: consumerID)
    }

    private func releaseClaim() {
        guard let claimedPubkey else { return }
        profileHost?.releaseProfile(pubkey: claimedPubkey, consumerID: consumerID)
        self.claimedPubkey = nil
    }
}

struct NostrProfileName: View {
    @Environment(\.nostrProfileHost) private var profileHost

    let pubkey: String
    let font: Font
    let color: Color

    @State private var consumerID = "olas-profile-name.\(UUID().uuidString)"
    @State private var claimedPubkey: String?

    init(pubkey: String, font: Font = .body, color: Color = .primary) {
        self.pubkey = pubkey
        self.font = font
        self.color = color
    }

    var body: some View {
        Group {
            if let profile = profileHost?.profile(forPubkey: pubkey), !profile.display.isEmpty {
                Text(profile.display)
                    .font(font)
                    .foregroundStyle(color)
                    .lineLimit(1)
            } else {
                EmptyView()
            }
        }
        .task(id: pubkey) { claim(pubkey) }
        .onDisappear { releaseClaim() }
    }

    private func claim(_ pubkey: String) {
        if let claimedPubkey, claimedPubkey != pubkey {
            profileHost?.releaseProfile(pubkey: claimedPubkey, consumerID: consumerID)
        }
        claimedPubkey = pubkey
        profileHost?.claimProfile(pubkey: pubkey, consumerID: consumerID)
    }

    private func releaseClaim() {
        guard let claimedPubkey else { return }
        profileHost?.releaseProfile(pubkey: claimedPubkey, consumerID: consumerID)
        self.claimedPubkey = nil
    }
}

private enum NostrIdenticon {
    static func color(forPubkey pubkey: String) -> Color {
        let hue = Double(djb2(pubkey) % 360) / 360.0
        return Color(hue: hue, saturation: 0.55, brightness: 0.75)
    }

    static func initial(forPubkey pubkey: String) -> String {
        let trimmed = pubkey.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "?" }
        return String(trimmed.prefix(1)).uppercased()
    }

    private static func djb2(_ value: String) -> UInt32 {
        var hash: UInt32 = 5381
        for byte in value.utf8 {
            hash = (hash &* 33) &+ UInt32(byte)
        }
        return hash
    }
}
