import SwiftUI

/// Block-level group produced by walking the roots of a `ContentTreeWire`.
/// `NostrContentView` consumes these in order; the grouping logic exists in a
/// separate file so the renderer view itself stays focused on layout.
public enum NostrContentGroup: Equatable {
    /// Inline runs that should be concatenated with `Text + Text`. The
    /// `[UInt32]` values are node indices; `.init(UInt32.max)` is a sentinel
    /// trailing newline used to separate adjacent paragraphs/headings.
    case inline(level: NostrContentInlineLevel, children: [UInt32])
    case media(urls: [String], kind: NostrMediaKind)
    case eventRef(NostrWireUri)
    case codeBlock(info: String?, body: String)
    case blockQuote(children: [UInt32])
    case list(orderedStart: UInt64?, items: [[UInt32]])
    case rule
    case image(alt: String, title: String?, src: String?)
    case placeholder(reason: NostrWirePlaceholderReason)
}

/// Display level for inline-text groups; lets the renderer pick paragraph vs
/// heading typography without re-walking the arena.
public enum NostrContentInlineLevel: Equatable {
    case paragraph
    case heading(level: UInt8)
}

/// Sentinel index used to inject a trailing newline between contiguous inline
/// groups (paragraph/heading). Index `UInt32.max` cannot reference a real
/// arena slot — the bounds check in `node(at:)` rejects it — so renderers can
/// safely treat it as a newline marker.
public let nostrContentNewlineSentinel: UInt32 = .max

/// Flatten the document into the block-level sequence consumed by
/// `NostrContentView`.
public func nostrContentGroups(_ tree: ContentTreeWire) -> [NostrContentGroup] {
    var groups: [NostrContentGroup] = []
    var pendingChildren: [UInt32] = []
    var pendingLevel: NostrContentInlineLevel = .paragraph

    func flush() {
        if !pendingChildren.isEmpty {
            groups.append(.inline(level: pendingLevel, children: pendingChildren))
            pendingChildren = []
            pendingLevel = .paragraph
        }
    }

    func appendInline(level: NostrContentInlineLevel, children: [UInt32]) {
        // Different inline-block levels can't share a paragraph; flush first.
        if !pendingChildren.isEmpty, pendingLevel != level {
            flush()
        }
        pendingLevel = level

        let startCount = pendingChildren.count
        for child in children {
            guard let childNode = tree.node(at: child) else { continue }
            if case .eventRef(let uri) = childNode {
                flush()
                groups.append(.eventRef(uri))
            } else {
                pendingChildren.append(child)
            }
        }
        if pendingChildren.count > startCount {
            pendingChildren.append(nostrContentNewlineSentinel)
        }
    }

    for root in tree.roots {
        guard let node = tree.node(at: root) else { continue }
        switch node {
        case .paragraph(let children):
            appendInline(level: .paragraph, children: children)
        case .heading(let level, let children):
            flush()
            appendInline(level: .heading(level: level), children: children)
            flush()
        case .media(let urls, let kind):
            flush()
            groups.append(.media(urls: urls, kind: kind))
        case .eventRef(let uri):
            flush()
            groups.append(.eventRef(uri))
        case .codeBlock(let info, let body):
            flush()
            groups.append(.codeBlock(info: info, body: body))
        case .blockQuote(let children):
            flush()
            groups.append(.blockQuote(children: children))
        case .list(let orderedStart, let items):
            flush()
            groups.append(.list(orderedStart: orderedStart, items: items))
        case .rule:
            flush()
            groups.append(.rule)
        case .image(let alt, let title, let src):
            flush()
            groups.append(.image(alt: alt, title: title, src: src))
        case .placeholder(let reason):
            flush()
            groups.append(.placeholder(reason: reason))
        default:
            // Bare inline node sitting at the root (e.g. plain text with no
            // surrounding paragraph). Treat it as a one-child paragraph.
            appendInline(level: .paragraph, children: [root])
        }
    }
    flush()
    return groups
}
