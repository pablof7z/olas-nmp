import SwiftUI
import Foundation

/// Circular avatar for a Nostr pubkey. Shows the profile picture when the
/// host projection has it; falls back to a deterministic identicon derived
/// from `pubkey`.
///
/// Replace `AsyncImage` with your own image cache (Kingfisher, Nuke, etc.)
/// if you already have one — the identicon fallback is self-contained.
///
/// Depends on `swiftui/user-avatar` for `ProfileWire` and `NostrProfileHost`.
public struct NostrAvatar: View {
    @Environment(\.nostrProfileHost) private var profileHost

    public let pubkey: String
    public let pictureUrl: URL?
    public let size: CGFloat
    public let consumerID: String?
    @State private var generatedConsumerID: String
    @State private var claimedPubkey: String?

    public init(
        pubkey: String,
        pictureUrl: URL? = nil,
        size: CGFloat = 40,
        consumerID: String? = nil
    ) {
        self.pubkey = pubkey
        self.pictureUrl = pictureUrl
        self.size = size
        self.consumerID = consumerID
        self._generatedConsumerID = State(
            initialValue: consumerID ?? "nostr-avatar.\(UUID().uuidString)"
        )
        self._claimedPubkey = State(initialValue: nil)
    }

    public init(profile: ProfileWire, size: CGFloat = 40) {
        self.pubkey = profile.pubkey
        self.pictureUrl = profile.avatarURL
        self.size = size
        self.consumerID = nil
        self._generatedConsumerID = State(
            initialValue: "nostr-avatar.static.\(UUID().uuidString)"
        )
        self._claimedPubkey = State(initialValue: nil)
    }

    public var body: some View {
        let url = pictureUrl ?? profileHost?.profile(forPubkey: pubkey)?.avatarURL

        Group {
            if let url {
                CachedImage(url: url, loading: { identicon }, failure: { identicon })
            } else {
                identicon
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
        .task(id: pubkey) {
            await MainActor.run {
                if let claimedPubkey, claimedPubkey != pubkey {
                    profileHost?.releaseProfile(
                        pubkey: claimedPubkey,
                        consumerID: generatedConsumerID
                    )
                }
                claimedPubkey = pubkey
                profileHost?.claimProfile(pubkey: pubkey, consumerID: generatedConsumerID)
            }
        }
        .onDisappear {
            if let claimedPubkey {
                profileHost?.releaseProfile(pubkey: claimedPubkey, consumerID: generatedConsumerID)
                self.claimedPubkey = nil
            }
        }
    }

    private var identicon: some View {
        ZStack {
            Circle().fill(NostrIdenticon.color(forPubkey: pubkey))
            Text(NostrIdenticon.initials(forPubkey: pubkey))
                .font(.system(size: size * 0.35, weight: .semibold))
                .foregroundStyle(.white)
        }
    }
}

// NostrIdenticon is defined in ContentTreeWire.swift
