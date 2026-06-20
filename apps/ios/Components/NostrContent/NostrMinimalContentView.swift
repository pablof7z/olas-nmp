import SwiftUI

public struct NostrContentRun: Identifiable, Equatable, RenderIdentifiable {
    public enum Kind: Equatable {
        case text
        case mention(pubkey: String)
        case hashtag(String)
        case link(URL)
    }

    public var id: String
    public var label: String
    public var kind: Kind

    public init(id: String, label: String, kind: Kind) {
        self.id = id
        self.label = label
        self.kind = kind
    }

    public func rendersIdentically(_ other: Self) -> Bool {
        self.id == other.id
            && self.label == other.label
            && self.kind == other.kind
    }
}

public struct NostrMinimalContentView: View {
    public var runs: [NostrContentRun]
    @Environment(\.nostrContentRenderer) private var renderer

    public init(runs: [NostrContentRun]) {
        self.runs = runs
    }

    public var body: some View {
        FlowLayout(spacing: 4) {
            ForEach(runs) { run in
                EquatableRow(model: run) { model in
                    runView(model)
                }
                .equatable()
            }
        }
    }

    @ViewBuilder
    private func runView(_ run: NostrContentRun) -> some View {
        switch run.kind {
        case .text:
            Text(run.label)
                .foregroundStyle(renderer.textColor)
        case .mention(let pubkey):
            Button(run.label) {
                renderer.callbacks.onMentionTap(pubkey)
            }
            .buttonStyle(.plain)
            .foregroundStyle(renderer.mentionColor)
        case .hashtag(let tag):
            Button(run.label) {
                renderer.callbacks.onHashtagTap(tag)
            }
            .buttonStyle(.plain)
            .foregroundStyle(renderer.hashtagColor)
        case .link(let url):
            Button(run.label) {
                renderer.callbacks.onLinkTap(url)
            }
            .buttonStyle(.plain)
            .foregroundStyle(renderer.linkColor)
        }
    }
}

private struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        layout(in: proposal.replacingUnspecifiedDimensions().width, subviews: subviews).size
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rows = layout(in: bounds.width, subviews: subviews).rows
        for row in rows {
            for item in row.items {
                subviews[item.index].place(
                    at: CGPoint(x: bounds.minX + item.origin.x, y: bounds.minY + item.origin.y),
                    proposal: ProposedViewSize(item.size)
                )
            }
        }
    }

    private func layout(in width: CGFloat, subviews: Subviews) -> FlowResult {
        var rows: [FlowRow] = []
        var current = FlowRow()
        var cursor = CGPoint.zero
        var rowHeight: CGFloat = 0

        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if cursor.x > 0, cursor.x + size.width > width {
                rows.append(current)
                cursor = CGPoint(x: 0, y: cursor.y + rowHeight + spacing)
                rowHeight = 0
                current = FlowRow()
            }

            current.items.append(FlowItem(index: index, origin: cursor, size: size))
            cursor.x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }

        rows.append(current)
        let height = rows.last?.items.last.map { $0.origin.y + rowHeight } ?? 0
        return FlowResult(rows: rows, size: CGSize(width: width, height: height))
    }
}

private struct FlowResult {
    var rows: [FlowRow]
    var size: CGSize
}

private struct FlowRow {
    var items: [FlowItem] = []
}

private struct FlowItem {
    var index: Int
    var origin: CGPoint
    var size: CGSize
}

// MARK: - ContentTreeWire → [NostrContentRun] adapter

public extension ContentTreeWire {
    /// Flatten a `ContentTreeWire` into a list of `NostrContentRun` values
    /// suitable for `NostrMinimalContentView`. Walks the arena starting from
    /// `roots`, collapses inline-only structure (paragraph / heading /
    /// emphasis / strong / link / softBreak / hardBreak) into runs, and
    /// skips block-level nodes the minimal renderer doesn't support
    /// (`media`, `image`, `invoice`, `codeBlock`, `rule`, `eventRef`,
    /// `placeholder`, `list`, `blockQuote`).
    ///
    /// `softBreak` becomes a single space run; `hardBreak` becomes a
    /// `\n` text run so the `FlowLayout` can break visually. Emphasis /
    /// strong wrappers contribute only their children's text (no styling
    /// in the minimal view).
    func nostrMinimalRuns() -> [NostrContentRun] {
        var runs: [NostrContentRun] = []
        var counter = 0
        for index in roots {
            walkMinimal(index: index, runs: &runs, counter: &counter)
        }
        return runs
    }

    private func walkMinimal(
        index: UInt32,
        runs: inout [NostrContentRun],
        counter: inout Int
    ) {
        guard let node = node(at: index) else { return }
        switch node {
        case .text(let value):
            appendText(value, runs: &runs, counter: &counter)
        case .mention(let uri):
            let label = "@\(shortMentionLabel(uri))"
            append(label: label, kind: .mention(pubkey: uri.primaryId), runs: &runs, counter: &counter)
        case .hashtag(let tag):
            append(label: "#\(tag)", kind: .hashtag(tag), runs: &runs, counter: &counter)
        case .url(let value):
            if let url = URL(string: value) {
                append(label: value, kind: .link(url), runs: &runs, counter: &counter)
            } else {
                appendText(value, runs: &runs, counter: &counter)
            }
        case .link(let children, let href):
            // Fold the children into a single text label; if the href is a
            // valid URL emit one `.link` run with that label, otherwise emit
            // the children as plain text (preserves the visible label).
            let label = childrenText(children)
            if let href, !href.isEmpty, let url = URL(string: href) {
                append(label: label, kind: .link(url), runs: &runs, counter: &counter)
            } else if !label.isEmpty {
                appendText(label, runs: &runs, counter: &counter)
            }
        case .emoji(let shortcode, _):
            // Minimal view has no UIImage surface — fall back to literal
            // `:shortcode:` text so the run is still searchable / copyable.
            appendText(":\(shortcode):", runs: &runs, counter: &counter)
        case .emphasis(let children),
             .strong(let children),
             .paragraph(let children),
             .heading(_, let children):
            for child in children {
                walkMinimal(index: child, runs: &runs, counter: &counter)
            }
        case .softBreak:
            appendText(" ", runs: &runs, counter: &counter)
        case .hardBreak:
            appendText("\n", runs: &runs, counter: &counter)
        case .inlineCode(let value):
            appendText(value, runs: &runs, counter: &counter)
        case .image(let alt, _, _):
            // Block-level image — minimal view doesn't render images, but
            // surface the alt text so the run isn't silently dropped.
            if !alt.isEmpty {
                appendText("[\(alt)]", runs: &runs, counter: &counter)
            }
        case .eventRef,
             .invoice,
             .blockQuote,
             .codeBlock,
             .list,
             .rule,
             .media,
             .placeholder:
            // Block-level / non-inline nodes the minimal renderer doesn't
            // support. Apps wanting the full surface should use
            // `NostrContentView` instead.
            break
        }
    }

    private func childrenText(_ children: [UInt32]) -> String {
        var out = ""
        for index in children {
            collectText(index: index, into: &out)
        }
        return out
    }

    private func collectText(index: UInt32, into out: inout String) {
        guard let node = node(at: index) else { return }
        switch node {
        case .text(let value), .inlineCode(let value), .url(let value):
            out += value
        case .hashtag(let tag):
            out += "#\(tag)"
        case .emoji(let shortcode, _):
            out += ":\(shortcode):"
        case .mention(let uri):
            out += "@\(shortMentionLabel(uri))"
        case .emphasis(let children),
             .strong(let children),
             .paragraph(let children),
             .heading(_, let children),
             .link(let children, _):
            for child in children {
                collectText(index: child, into: &out)
            }
        case .softBreak:
            out += " "
        case .hardBreak:
            out += "\n"
        default:
            break
        }
    }

    private func appendText(_ value: String, runs: inout [NostrContentRun], counter: inout Int) {
        // Coalesce adjacent text runs so consumers don't see an artificial
        // run boundary between two `.text` segments emitted by the walker.
        if let last = runs.last, last.kind == .text {
            var merged = last
            merged.label += value
            runs[runs.count - 1] = merged
            return
        }
        append(label: value, kind: .text, runs: &runs, counter: &counter)
    }

    private func append(
        label: String,
        kind: NostrContentRun.Kind,
        runs: inout [NostrContentRun],
        counter: inout Int
    ) {
        runs.append(NostrContentRun(id: "run-\(counter)", label: label, kind: kind))
        counter += 1
    }

    /// Inline short mention label so this extension doesn't need to pull in
    /// `content-view`. Mirrors `NostrContentView.defaultMentionLabel`: shows
    /// the full pubkey when ≤12 chars, otherwise `prefix(8)…suffix(4)`.
    private func shortMentionLabel(_ uri: NostrWireUri) -> String {
        let value = uri.primaryId
        guard value.count > 12 else { return value }
        return "\(value.prefix(8))…\(value.suffix(4))"
    }
}
