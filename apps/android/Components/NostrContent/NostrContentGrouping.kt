// Requires: nothing beyond the Kotlin stdlib. Kotlin 1.9+.
//
// Block-level grouping for a `ContentTreeWire`. `NostrContentView` consumes
// these in order; the grouping logic lives in its own file so the renderer
// composable stays focused on layout.

package nmp.content

/** Display level for inline-text groups; lets the renderer pick paragraph vs
 *  heading typography without re-walking the arena. */
public sealed class NostrContentInlineLevel {
    public object Paragraph : NostrContentInlineLevel()
    public data class Heading(val level: UByte) : NostrContentInlineLevel()
}

/** Block-level group produced by walking the roots of a [ContentTreeWire]. */
public sealed class NostrContentGroup {
    /** Inline runs that should be concatenated. The [children] are node
     *  indices; `UInt.MAX_VALUE` is a sentinel trailing newline used to
     *  separate adjacent paragraphs / headings. */
    public data class Inline(
        val level: NostrContentInlineLevel,
        val children: List<UInt>,
    ) : NostrContentGroup()

    public data class MediaGroup(val urls: List<String>, val kind: MediaKind) : NostrContentGroup()
    public data class EventRefGroup(val uri: WireNostrUri) : NostrContentGroup()
    public data class CodeBlockGroup(val info: String?, val body: String) : NostrContentGroup()
    public data class BlockQuoteGroup(val children: List<UInt>) : NostrContentGroup()
    public data class ListGroup(
        val orderedStart: ULong?,
        val items: List<List<UInt>>,
    ) : NostrContentGroup()
    public object RuleGroup : NostrContentGroup()
    public data class ImageGroup(
        val alt: String,
        val title: String?,
        val src: String?,
    ) : NostrContentGroup()
    public data class PlaceholderGroup(val reason: PlaceholderReason) : NostrContentGroup()
}

/** Sentinel index used to inject a trailing newline between contiguous inline
 *  groups (paragraph / heading). `UInt.MAX_VALUE` cannot reference a real
 *  arena slot — the bounds check in [ContentTreeWire.nodeAt] rejects it — so
 *  renderers can safely treat it as a newline marker. */
public val NOSTR_CONTENT_NEWLINE_SENTINEL: UInt = UInt.MAX_VALUE

/**
 * Flatten the document into a sequence of block-level groups. Mirrors the
 * `nostrContentGroups` function in the SwiftUI registry. Inline event refs
 * appearing inside paragraphs are pulled out into their own `EventRefGroup`
 * so the quote card renders block-level.
 */
public fun nostrContentGroups(tree: ContentTreeWire): List<NostrContentGroup> {
    val groups = mutableListOf<NostrContentGroup>()
    var pendingChildren = mutableListOf<UInt>()
    var pendingLevel: NostrContentInlineLevel = NostrContentInlineLevel.Paragraph

    fun flush() {
        if (pendingChildren.isNotEmpty()) {
            groups.add(NostrContentGroup.Inline(pendingLevel, pendingChildren.toList()))
            pendingChildren = mutableListOf()
            pendingLevel = NostrContentInlineLevel.Paragraph
        }
    }

    fun appendInline(level: NostrContentInlineLevel, children: List<UInt>) {
        if (pendingChildren.isNotEmpty() && pendingLevel != level) {
            flush()
        }
        pendingLevel = level

        val startCount = pendingChildren.size
        for (child in children) {
            val childNode = tree.nodeAt(child) ?: continue
            if (childNode is WireNode.EventRef) {
                flush()
                groups.add(NostrContentGroup.EventRefGroup(childNode.uri))
            } else {
                pendingChildren.add(child)
            }
        }
        if (pendingChildren.size > startCount) {
            pendingChildren.add(NOSTR_CONTENT_NEWLINE_SENTINEL)
        }
    }

    for (root in tree.roots) {
        val node = tree.nodeAt(root) ?: continue
        when (node) {
            is WireNode.Paragraph -> appendInline(NostrContentInlineLevel.Paragraph, node.children)
            is WireNode.Heading -> {
                flush()
                appendInline(NostrContentInlineLevel.Heading(node.level), node.children)
                flush()
            }
            is WireNode.Media -> {
                flush()
                groups.add(NostrContentGroup.MediaGroup(node.urls, node.mediaKind))
            }
            is WireNode.EventRef -> {
                flush()
                groups.add(NostrContentGroup.EventRefGroup(node.uri))
            }
            is WireNode.CodeBlock -> {
                flush()
                groups.add(NostrContentGroup.CodeBlockGroup(node.info, node.body))
            }
            is WireNode.BlockQuote -> {
                flush()
                groups.add(NostrContentGroup.BlockQuoteGroup(node.children))
            }
            is WireNode.ListNode -> {
                flush()
                groups.add(NostrContentGroup.ListGroup(node.orderedStart, node.items))
            }
            WireNode.Rule -> {
                flush()
                groups.add(NostrContentGroup.RuleGroup)
            }
            is WireNode.Image -> {
                flush()
                groups.add(NostrContentGroup.ImageGroup(node.alt, node.title, node.src))
            }
            is WireNode.Placeholder -> {
                flush()
                groups.add(NostrContentGroup.PlaceholderGroup(node.reason))
            }
            // Bare inline node at the root (plain text without paragraph wrap).
            // Treat it as a one-child paragraph.
            is WireNode.Text,
            is WireNode.Mention,
            is WireNode.Hashtag,
            is WireNode.Url,
            is WireNode.Emoji,
            is WireNode.Invoice,
            is WireNode.Emphasis,
            is WireNode.Strong,
            is WireNode.InlineCode,
            is WireNode.Link,
            WireNode.SoftBreak,
            WireNode.HardBreak -> appendInline(NostrContentInlineLevel.Paragraph, listOf(root))
        }
    }
    flush()
    return groups
}
