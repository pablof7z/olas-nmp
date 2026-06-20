import AVKit
import SwiftUI
import UIKit

/// SwiftUI renderer for a `ContentTreeWire`. Walks `tree.roots`, flattens the
/// arena into block-level groups via `nostrContentGroups`, and renders each
/// block (paragraph/heading/media/code/list/quote/rule/image/event-ref/
/// placeholder) with SwiftUI `Text` concatenation for inline runs.
///
/// Data injection contract:
///   • Theming + tap callbacks come from `NostrContentRenderer` in the
///     SwiftUI environment (see `swiftui/content-core`).
///   • Mention display labels are provided by the app via `mentionLabel`.
///   • Quote/embed cards are app-owned: the app passes a `quoteCardProvider`
///     closure that returns a `NostrQuoteCardModel` for an event-ref URI.
///     When `nil`, the view falls back to a `.collapsed` quote card.
public struct NostrContentView: View {
    public var tree: ContentTreeWire
    public var font: Font
    public var mentionLabel: (NostrWireUri) -> String
    public var quoteCardProvider: ((NostrWireUri) -> NostrQuoteCardModel?)?

    @Environment(\.nostrContentRenderer) private var renderer

    public init(
        tree: ContentTreeWire,
        font: Font = .body,
        mentionLabel: @escaping (NostrWireUri) -> String = NostrContentView.defaultMentionLabel,
        quoteCardProvider: ((NostrWireUri) -> NostrQuoteCardModel?)? = nil
    ) {
        self.tree = tree
        self.font = font
        self.mentionLabel = mentionLabel
        self.quoteCardProvider = quoteCardProvider
    }

    public var body: some View {
        let groups = nostrContentGroups(tree)
        if groups.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                    groupView(group)
                }
            }
        }
    }

    // MARK: - Group dispatch

    @ViewBuilder
    private func groupView(_ group: NostrContentGroup) -> some View {
        switch group {
        case .inline(let level, let children):
            inlineGroup(level: level, children: children)
        case .media(let urls, let kind):
            mediaGroup(urls: urls, kind: kind)
        case .eventRef(let uri):
            eventRefView(uri)
        case .codeBlock(let info, let body):
            codeBlockView(info: info, body: body)
        case .blockQuote(let children):
            blockQuoteView(children: children)
        case .list(let orderedStart, let items):
            listView(orderedStart: orderedStart, items: items)
        case .rule:
            ruleView
        case .image(let alt, let title, let src):
            imageBlockView(alt: alt, title: title, src: src)
        case .placeholder(let reason):
            placeholderChip(reason: reason)
        }
    }

    @ViewBuilder
    private func inlineGroup(level: NostrContentInlineLevel, children: [UInt32]) -> some View {
        let concatenated = children.reduce(Text("")) { acc, child in
            acc + inlineText(child)
        }
        switch level {
        case .paragraph:
            concatenated
                .font(font)
                .foregroundStyle(renderer.textColor)
                .fixedSize(horizontal: false, vertical: true)
        case .heading(let lvl):
            concatenated
                .font(headingFont(for: lvl))
                .foregroundStyle(renderer.textColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Inline text concatenation

    /// Convert one arena node into a `Text` value that can be `+`-concatenated
    /// with neighbouring runs. Recursive children (emphasis/strong/link/heading
    /// /paragraph) are walked here so the whole inline subtree collapses to a
    /// single `Text`.
    public func inlineText(_ index: UInt32) -> Text {
        if index == nostrContentNewlineSentinel { return Text("\n") }
        guard let node = tree.node(at: index) else { return Text("") }
        switch node {
        case .text(let value):
            return Text(value)
        case .mention(let uri):
            return Text("@\(mentionLabel(uri))")
                .foregroundStyle(renderer.mentionColor)
                .bold()
        case .eventRef(let uri):
            return Text("↩ \(shortEntity(uri.primaryId))")
                .foregroundStyle(renderer.linkColor)
                .bold()
        case .hashtag(let tag):
            return Text("#\(tag)")
                .foregroundStyle(renderer.hashtagColor)
                .bold()
        case .url(let value):
            return Text(value).foregroundStyle(renderer.linkColor)
        case .emoji(let shortcode, _):
            // Apps fill `renderer.emojiImages` from kind:0 / NIP-30 tag data
            // and the inline run renders the image directly. Empty dict
            // (the default) falls back to the literal `:shortcode:` text so
            // unwired apps still get a readable surface.
            if let img = renderer.emojiImages[shortcode] {
                return Text(Image(uiImage: img))
            }
            return Text(":\(shortcode):")
        case .invoice:
            return Text("⚡ invoice").foregroundStyle(renderer.linkColor)
        case .emphasis(let children):
            return children.reduce(Text("")) { $0 + inlineText($1).italic() }
        case .strong(let children):
            return children.reduce(Text("")) { $0 + inlineText($1).bold() }
        case .inlineCode(let value):
            return Text(value).font(.system(.body, design: .monospaced))
        case .link(let children, let href):
            let label = children.reduce(Text("")) { $0 + inlineText($1) }
            if let href, !href.isEmpty {
                return label
                    .foregroundStyle(renderer.linkColor)
                    .underline()
            }
            return label
        case .image(let alt, _, _):
            return Text(alt.isEmpty ? "[image]" : "[\(alt)]")
                .foregroundStyle(renderer.placeholderColor)
        case .softBreak:
            return Text(" ")
        case .hardBreak:
            return Text("\n")
        case .paragraph(let children),
             .heading(_, let children),
             .blockQuote(let children):
            return children.reduce(Text("")) { $0 + inlineText($1) }
        case .list, .codeBlock, .rule, .media, .placeholder:
            // Block-level — should never appear inside an inline reduce. Emit
            // nothing rather than break the text concatenation.
            return Text("")
        }
    }

    // MARK: - Block builders

    @ViewBuilder
    private func mediaGroup(urls: [String], kind: NostrMediaKind) -> some View {
        switch kind {
        case .image:
            let parsed = urls.compactMap(URL.init(string:))
            if !parsed.isEmpty {
                NostrMediaGrid(imageUrls: parsed)
            }
        case .video:
            // Inline playback via `AVKit.VideoPlayer` so video media nodes
            // render with native scrub / fullscreen controls. Audio stays on
            // the compact link-style row (no waveform UI in v1).
            if let first = urls.first.flatMap(URL.init(string:)) {
                VideoPlayer(player: AVPlayer(url: first))
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        case .audio:
            if let first = urls.first.flatMap(URL.init(string:)) {
                mediaRow(first, systemImage: "speaker.wave.2.fill")
            }
        }
    }

    private func mediaRow(_ url: URL, systemImage: String) -> some View {
        Button {
            renderer.callbacks.onLinkTap(url)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.title2)
                    .foregroundStyle(renderer.linkColor)
                Text(url.lastPathComponent)
                    .font(.caption.monospaced())
                    .foregroundStyle(renderer.secondaryTextColor)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity)
            .background(renderer.codeBackgroundColor, in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    private func eventRefView(_ uri: NostrWireUri) -> some View {
        // Variant selection is a three-branch decision so the `.missing`
        // variant is reachable:
        //   • no provider wired       → `.collapsed` (View quote affordance)
        //   • provider returned model → `.rich`
        //   • provider returned nil   → `.missing` (Content unavailable + URI)
        let providedModel = quoteCardProvider?(uri)
        let variant: NostrQuoteCardVariant
        let model: NostrQuoteCardModel
        if quoteCardProvider == nil {
            variant = .collapsed
            model = NostrQuoteCardModel(id: uri.primaryId, unresolvedUri: uri.uri)
        } else if let providedModel {
            variant = .rich
            model = providedModel
        } else {
            variant = .missing
            model = NostrQuoteCardModel(id: uri.primaryId, unresolvedUri: uri.uri)
        }
        return NostrQuoteCard(
            model: model,
            variant: variant,
            onTap: { renderer.callbacks.onEventRefTap(uri.primaryId) }
        )
    }

    private func codeBlockView(info: String?, body: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let info, !info.isEmpty {
                Text(info)
                    .font(.caption2.monospaced())
                    .foregroundStyle(renderer.secondaryTextColor)
            }
            Text(body)
                .font(.system(.callout, design: .monospaced))
                .foregroundStyle(renderer.textColor)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(10)
        .background(renderer.codeBackgroundColor, in: RoundedRectangle(cornerRadius: 6))
    }

    private func blockQuoteView(children: [UInt32]) -> some View {
        let text = children.reduce(Text("")) { $0 + inlineText($1) }
        return HStack(spacing: 10) {
            Rectangle()
                .fill(renderer.quoteBorderColor)
                .frame(width: 3)
            text
                .font(font.italic())
                .foregroundStyle(renderer.secondaryTextColor)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func listView(orderedStart: UInt64?, items: [[UInt32]]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(items.enumerated()), id: \.offset) { offset, children in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(marker(orderedStart: orderedStart, offset: offset))
                        .font(font)
                        .foregroundStyle(renderer.secondaryTextColor)
                    children.reduce(Text("")) { $0 + inlineText($1) }
                        .font(font)
                        .foregroundStyle(renderer.textColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private func marker(orderedStart: UInt64?, offset: Int) -> String {
        if let orderedStart {
            return "\(orderedStart + UInt64(offset))."
        }
        return "•"
    }

    private var ruleView: some View {
        Rectangle()
            .fill(renderer.quoteBorderColor)
            .frame(height: 1)
            .padding(.vertical, 4)
    }

    private func imageBlockView(alt: String, title: String?, src: String?) -> some View {
        if let src, let url = URL(string: src) {
            return AnyView(
                NostrMediaGrid(imageUrls: [url])
                    .accessibilityLabel(title ?? alt)
            )
        }
        return AnyView(
            Text(alt.isEmpty ? "[image]" : "[\(alt)]")
                .font(.caption)
                .foregroundStyle(renderer.placeholderColor)
        )
    }

    private func placeholderChip(reason: NostrWirePlaceholderReason) -> some View {
        let label: String
        let icon: String
        switch reason {
        case .depthLimit:
            label = "Nested content collapsed"
            icon = "chevron.down.square"
        case .unresolvedUri:
            label = "Unresolved reference"
            icon = "questionmark.square.dashed"
        }
        return HStack(spacing: 6) {
            Image(systemName: icon)
                .foregroundStyle(renderer.placeholderColor)
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(renderer.placeholderColor)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(renderer.codeBackgroundColor, in: Capsule())
    }

    // MARK: - Defaults / helpers

    public nonisolated static func defaultMentionLabel(_ uri: NostrWireUri) -> String {
        let value = uri.primaryId
        guard value.count > 12 else { return value }
        return "\(value.prefix(8))…\(value.suffix(4))"
    }

    private func headingFont(for level: UInt8) -> Font {
        switch level {
        case 1: return .largeTitle.weight(.bold)
        case 2: return .title.weight(.bold)
        case 3: return .title2.weight(.semibold)
        case 4: return .title3.weight(.semibold)
        case 5: return .headline
        default: return .subheadline.weight(.semibold)
        }
    }

    private func shortEntity(_ value: String) -> String {
        guard value.count > 12 else { return value }
        return "\(value.prefix(8))…\(value.suffix(4))"
    }
}
