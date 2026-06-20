import SwiftUI

/// Variant of the quote/embed card. Each style is a stable rendering contract
/// the embedding app can switch between without re-deriving data.
public enum NostrQuoteCardVariant: Equatable {
    /// Single-line `View quote` affordance; expands to `.rich` on tap when the
    /// app wires the `onExpand` closure.
    case collapsed
    /// Author + truncated content text, framed only by a border.
    case compact
    /// Author avatar stub + name + truncated content + optional media thumb.
    case rich
    /// "Content unavailable" placeholder, surfaces `unresolvedUri` to the user.
    case missing
}

/// Data model the embedding app populates per quote card. The card view is
/// pure — it never fetches network state. Apps pass a closure or environment
/// value into their data layer to hydrate `NostrQuoteCardModel` instances.
public struct NostrQuoteCardModel: Equatable, @unchecked Sendable {
    public var id: String
    public var unresolvedUri: String?
    public var authorPubkey: String?
    public var authorDisplayName: String?
    public var authorAvatarUrl: URL?
    public var content: String
    public var mediaThumbnailUrl: URL?
    public var createdAtDisplay: String?

    public init(
        id: String,
        unresolvedUri: String? = nil,
        authorPubkey: String? = nil,
        authorDisplayName: String? = nil,
        authorAvatarUrl: URL? = nil,
        content: String = "",
        mediaThumbnailUrl: URL? = nil,
        createdAtDisplay: String? = nil
    ) {
        self.id = id
        self.unresolvedUri = unresolvedUri
        self.authorPubkey = authorPubkey
        self.authorDisplayName = authorDisplayName
        self.authorAvatarUrl = authorAvatarUrl
        self.content = content
        self.mediaThumbnailUrl = mediaThumbnailUrl
        self.createdAtDisplay = createdAtDisplay
    }

    public static let missing = NostrQuoteCardModel(id: "")
}

/// Reusable quote / embedded-event card. The app is responsible for hydrating
/// the model; the card only renders.
public struct NostrQuoteCard: View {
    public var model: NostrQuoteCardModel
    public var variant: NostrQuoteCardVariant
    public var onTap: (() -> Void)?
    public var onExpand: (() -> Void)?

    @Environment(\.nostrContentRenderer) private var renderer

    public init(
        model: NostrQuoteCardModel,
        variant: NostrQuoteCardVariant = .rich,
        onTap: (() -> Void)? = nil,
        onExpand: (() -> Void)? = nil
    ) {
        self.model = model
        self.variant = variant
        self.onTap = onTap
        self.onExpand = onExpand
    }

    public var body: some View {
        switch variant {
        case .collapsed: collapsedBody
        case .compact:   compactBody
        case .rich:      richBody
        case .missing:   missingBody
        }
    }

    // MARK: variants

    private var collapsedBody: some View {
        Button {
            (onExpand ?? onTap ?? defaultTap)()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "quote.bubble")
                    .foregroundStyle(renderer.linkColor)
                Text("View quote")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(renderer.linkColor)
                Spacer(minLength: 0)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(renderer.quoteBackgroundColor, in: RoundedRectangle(cornerRadius: 8))
            .overlay(border)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("View quoted post")
    }

    private var compactBody: some View {
        cardButton {
            VStack(alignment: .leading, spacing: 4) {
                Text("@\(authorLabel)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(renderer.textColor)
                Text(truncated(model.content, limit: 140))
                    .font(.callout)
                    .foregroundStyle(renderer.textColor)
                    .lineLimit(3)
            }
        }
    }

    private var richBody: some View {
        cardButton {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    avatar
                    VStack(alignment: .leading, spacing: 1) {
                        Text("@\(authorLabel)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(renderer.textColor)
                            .lineLimit(1)
                        if let display = model.createdAtDisplay {
                            Text(display)
                                .font(.caption2.monospaced())
                                .foregroundStyle(renderer.secondaryTextColor)
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(renderer.secondaryTextColor)
                }
                if !model.content.isEmpty {
                    Text(truncated(model.content, limit: 240))
                        .font(.callout)
                        .foregroundStyle(renderer.textColor)
                        .lineLimit(6)
                }
                if let thumb = model.mediaThumbnailUrl {
                    thumbnail(thumb)
                }
            }
        }
    }

    private var missingBody: some View {
        cardButton {
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.bubble")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(renderer.placeholderColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Content unavailable")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(renderer.textColor)
                    if let uri = model.unresolvedUri, !uri.isEmpty {
                        Text(uri)
                            .font(.caption2.monospaced())
                            .foregroundStyle(renderer.secondaryTextColor)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    // MARK: helpers

    private func cardButton<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        Button {
            (onTap ?? defaultTap)()
        } label: {
            content()
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(renderer.quoteBackgroundColor, in: RoundedRectangle(cornerRadius: 8))
                .overlay(border)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open quoted post")
    }

    private func defaultTap() {
        renderer.callbacks.onEventRefTap(model.id)
    }

    @ViewBuilder
    private var avatar: some View {
        if let avatarUrl = model.authorAvatarUrl {
            NostrImageView(url: avatarUrl)
                .frame(width: 26, height: 26)
                .clipShape(Circle())
        } else {
            avatarFallback
        }
    }

    private var avatarFallback: some View {
        NostrIdenticon.identiconView(
            forPubkey: model.authorPubkey ?? model.id,
            size: 26
        )
        .clipShape(Circle())
    }

    private func thumbnail(_ url: URL) -> some View {
        NostrImageView(url: url)
            .frame(maxWidth: .infinity, maxHeight: 160)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    private var border: some View {
        RoundedRectangle(cornerRadius: 8)
            .stroke(renderer.quoteBorderColor, lineWidth: 1.5)
    }

    private var authorLabel: String {
        if let display = model.authorDisplayName, !display.isEmpty {
            return display
        }
        if let pubkey = model.authorPubkey, !pubkey.isEmpty {
            return shortPubkey(pubkey)
        }
        return shortPubkey(model.id)
    }

    private func truncated(_ value: String, limit: Int) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > limit else { return trimmed }
        return "\(trimmed.prefix(limit))…"
    }

    private func shortPubkey(_ value: String) -> String {
        guard value.count > 12 else { return value }
        return "\(value.prefix(8))…\(value.suffix(4))"
    }
}
