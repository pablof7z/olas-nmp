import SwiftUI

/// Tappable profile mention chip. Renders `@displayName` and, when an avatar
/// URL is available, a small circular avatar alongside the name. Falls back to
/// a deterministic identicon color block when no URL is provided.
///
/// Depends on `swiftui/content-core` for the `NostrContentRenderer` environment
/// (colors + callbacks).
public struct NostrMentionChip: View {
    public var pubkey: String
    public var displayName: String?
    public var avatarUrl: URL?
    public var showsAvatar: Bool

    @Environment(\.nostrContentRenderer) private var renderer

    public init(
        pubkey: String,
        displayName: String? = nil,
        avatarUrl: URL? = nil,
        showsAvatar: Bool = true
    ) {
        self.pubkey = pubkey
        self.displayName = displayName
        self.avatarUrl = avatarUrl
        self.showsAvatar = showsAvatar
    }

    public var body: some View {
        Button {
            renderer.callbacks.onMentionTap(pubkey)
        } label: {
            HStack(spacing: 4) {
                if showsAvatar {
                    avatar
                }
                Text("@\(label)")
                    .foregroundStyle(renderer.mentionColor)
                    .fontWeight(.semibold)
                    .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Mention of \(label)")
    }

    private var label: String {
        if let displayName, !displayName.isEmpty {
            return displayName
        }
        return shortPubkey(pubkey)
    }

    @ViewBuilder
    private var avatar: some View {
        if let avatarUrl {
            NostrImageView(url: avatarUrl)
                .frame(width: 16, height: 16)
                .clipShape(Circle())
        } else {
            fallback
        }
    }

    private var fallback: some View {
        NostrIdenticon.identiconView(forPubkey: pubkey, size: 16)
            .clipShape(Circle())
    }

    private func shortPubkey(_ value: String) -> String {
        guard value.count > 12 else { return value }
        return "\(value.prefix(8))…\(value.suffix(4))"
    }
}
