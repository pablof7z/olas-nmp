// Requires: compose-ui, compose-foundation, compose-material3,
// androidx.compose.material:material-icons-extended (for ExpandMore,
// HelpOutline, PlayArrow, VolumeUp). Pulls `compose/content-media-grid` and
// `compose/content-quote-card` as registry deps. Kotlin 1.9+.
//
// Compose mirror of the SwiftUI `NostrContentView`. Walks a
// `ContentTreeWire`, flattens the arena into block-level groups via
// `nostrContentGroups`, and renders each block (paragraph / heading / media /
// code / list / quote / rule / image / event-ref / placeholder).
//
// Data injection contract:
//   - Theming + tap callbacks come from `LocalNostrContentRenderer`
//     (see `compose/content-core`).
//   - Mention display labels are provided by the app via `mentionLabel`.
//   - Quote / embed cards are app-owned: pass a `quoteCardProvider` closure
//     that returns a `NostrQuoteCardModel` for an event-ref URI. When `null`,
//     the view falls back to a `.Collapsed` quote card.
//
// Inline runs are flattened into a single `AnnotatedString` (with per-run
// styling) and shown as a `ClickableText` so tap-offset → annotation routing
// can dispatch the matching callback. Block nodes use `Column`.

package nmp.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

@Composable
public fun NostrContentView(
    tree: ContentTreeWire,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    mentionLabel: (WireNostrUri) -> String = ::defaultMentionLabel,
    quoteCardProvider: ((WireNostrUri) -> NostrQuoteCardModel?)? = null,
) {
    val groups = nostrContentGroups(tree)
    if (groups.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        for (group in groups) {
            RenderGroup(
                group = group,
                tree = tree,
                textStyle = textStyle,
                mentionLabel = mentionLabel,
                quoteCardProvider = quoteCardProvider,
            )
        }
    }
}

public fun defaultMentionLabel(uri: WireNostrUri): String {
    val value = uri.primaryId
    if (value.length <= 12) return value
    return "${value.take(8)}…${value.takeLast(4)}"
}

// ---------------------------------------------------------------------------
// Block dispatch
// ---------------------------------------------------------------------------

@Composable
private fun RenderGroup(
    group: NostrContentGroup,
    tree: ContentTreeWire,
    textStyle: TextStyle,
    mentionLabel: (WireNostrUri) -> String,
    quoteCardProvider: ((WireNostrUri) -> NostrQuoteCardModel?)?,
) {
    when (group) {
        is NostrContentGroup.Inline -> InlineGroup(
            level = group.level,
            children = group.children,
            tree = tree,
            textStyle = textStyle,
            mentionLabel = mentionLabel,
        )
        is NostrContentGroup.MediaGroup -> MediaGroupBlock(
            urls = group.urls,
            kind = group.kind,
        )
        is NostrContentGroup.EventRefGroup -> EventRefBlock(
            uri = group.uri,
            quoteCardProvider = quoteCardProvider,
        )
        is NostrContentGroup.CodeBlockGroup -> CodeBlockBlock(
            info = group.info,
            body = group.body,
        )
        is NostrContentGroup.BlockQuoteGroup -> BlockQuoteBlock(
            children = group.children,
            tree = tree,
            textStyle = textStyle,
            mentionLabel = mentionLabel,
        )
        is NostrContentGroup.ListGroup -> ListBlock(
            orderedStart = group.orderedStart,
            items = group.items,
            tree = tree,
            textStyle = textStyle,
            mentionLabel = mentionLabel,
        )
        NostrContentGroup.RuleGroup -> RuleBlock()
        is NostrContentGroup.ImageGroup -> ImageBlock(
            alt = group.alt,
            title = group.title,
            src = group.src,
        )
        is NostrContentGroup.PlaceholderGroup -> PlaceholderChip(reason = group.reason)
    }
}

// ---------------------------------------------------------------------------
// Inline rendering (Text + Text concatenation → AnnotatedString)
// ---------------------------------------------------------------------------

@Composable
private fun InlineGroup(
    level: NostrContentInlineLevel,
    children: List<UInt>,
    tree: ContentTreeWire,
    textStyle: TextStyle,
    mentionLabel: (WireNostrUri) -> String,
) {
    val renderer = LocalNostrContentRenderer.current
    val annotated = buildAnnotatedString {
        for (child in children) {
            appendInline(
                index = child,
                tree = tree,
                renderer = renderer,
                mentionLabel = mentionLabel,
            )
        }
    }
    val effectiveStyle = when (level) {
        NostrContentInlineLevel.Paragraph -> textStyle.copy(color = renderer.textColor)
        is NostrContentInlineLevel.Heading -> headingStyle(level.level).copy(color = renderer.textColor)
    }
    ClickableText(
        text = annotated,
        style = effectiveStyle,
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            dispatchInlineTap(annotated, offset, renderer)
        },
    )
}

/**
 * Append one arena node's inline projection to the [AnnotatedString] builder.
 * Recursive children (emphasis / strong / link / heading / paragraph) are
 * walked here so the whole inline subtree collapses into the running text.
 * Block-level nodes (`list`, `code_block`, `rule`, `media`, `placeholder`)
 * appearing inside an inline group emit nothing rather than break flow.
 */
private fun AnnotatedString.Builder.appendInline(
    index: UInt,
    tree: ContentTreeWire,
    renderer: NostrContentRenderer,
    mentionLabel: (WireNostrUri) -> String,
) {
    if (index == NOSTR_CONTENT_NEWLINE_SENTINEL) {
        append('\n')
        return
    }
    val node = tree.nodeAt(index) ?: return
    when (node) {
        is WireNode.Text -> append(node.text)
        is WireNode.Mention -> {
            val label = mentionLabel(node.uri)
            withAnnotationScope(MENTION_ANNOTATION, node.uri.primaryId) {
                withStyleScope(
                    SpanStyle(
                        color = renderer.mentionColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) { append("@$label") }
            }
        }
        is WireNode.EventRef -> {
            val short = shortEntity(node.uri.primaryId)
            withAnnotationScope(EVENT_REF_ANNOTATION, node.uri.primaryId) {
                withStyleScope(
                    SpanStyle(
                        color = renderer.linkColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) { append("↩ $short") }
            }
        }
        is WireNode.Hashtag -> {
            withAnnotationScope(HASHTAG_ANNOTATION, node.tag) {
                withStyleScope(
                    SpanStyle(
                        color = renderer.hashtagColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) { append("#${node.tag}") }
            }
        }
        is WireNode.Url -> {
            withAnnotationScope(LINK_ANNOTATION, node.url) {
                withStyleScope(SpanStyle(color = renderer.linkColor)) {
                    append(node.url)
                }
            }
        }
        is WireNode.Emoji -> append(":${node.shortcode}:")
        is WireNode.Invoice -> {
            withStyleScope(SpanStyle(color = renderer.linkColor)) {
                append("⚡ invoice")
            }
        }
        is WireNode.Emphasis -> withStyleScope(SpanStyle(fontStyle = FontStyle.Italic)) {
            for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
        }
        is WireNode.Strong -> withStyleScope(SpanStyle(fontWeight = FontWeight.Bold)) {
            for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
        }
        is WireNode.InlineCode -> withStyleScope(SpanStyle(fontFamily = FontFamily.Monospace)) {
            append(node.code)
        }
        is WireNode.Link -> {
            val href = node.href
            if (!href.isNullOrEmpty()) {
                withAnnotationScope(LINK_ANNOTATION, href) {
                    withStyleScope(
                        SpanStyle(
                            color = renderer.linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
                    }
                }
            } else {
                for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
            }
        }
        is WireNode.Image -> {
            val alt = node.alt
            withStyleScope(SpanStyle(color = renderer.placeholderColor)) {
                append(if (alt.isEmpty()) "[image]" else "[$alt]")
            }
        }
        WireNode.SoftBreak -> append(' ')
        WireNode.HardBreak -> append('\n')
        is WireNode.Paragraph -> for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
        is WireNode.Heading -> for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
        is WireNode.BlockQuote -> for (child in node.children) appendInline(child, tree, renderer, mentionLabel)
        is WireNode.ListNode,
        is WireNode.CodeBlock,
        WireNode.Rule,
        is WireNode.Media,
        is WireNode.Placeholder -> { /* block-level — never inside an inline reduce */ }
    }
}

/** Resolve a tap offset against the annotations attached during inline build. */
private fun dispatchInlineTap(
    annotated: AnnotatedString,
    offset: Int,
    renderer: NostrContentRenderer,
) {
    annotated.getStringAnnotations(MENTION_ANNOTATION, offset, offset).firstOrNull()?.let {
        renderer.callbacks.onMentionTap(it.item)
        return
    }
    annotated.getStringAnnotations(EVENT_REF_ANNOTATION, offset, offset).firstOrNull()?.let {
        renderer.callbacks.onEventRefTap(it.item)
        return
    }
    annotated.getStringAnnotations(HASHTAG_ANNOTATION, offset, offset).firstOrNull()?.let {
        renderer.callbacks.onHashtagTap(it.item)
        return
    }
    annotated.getStringAnnotations(LINK_ANNOTATION, offset, offset).firstOrNull()?.let {
        renderer.callbacks.onLinkTap(it.item)
    }
}

// Annotation tags used to round-trip per-run identifiers from
// AnnotatedString into the tap-offset dispatcher. Centralized so the
// builder and the dispatcher cannot drift.
private const val MENTION_ANNOTATION = "nmp:mention"
private const val EVENT_REF_ANNOTATION = "nmp:event_ref"
private const val HASHTAG_ANNOTATION = "nmp:hashtag"
private const val LINK_ANNOTATION = "nmp:link"

/** Push a [SpanStyle] for the duration of [block] then pop it. Avoids the
 *  `androidx.compose.ui.text.withStyle` import to keep this file's API
 *  surface explicit. */
private inline fun AnnotatedString.Builder.withStyleScope(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val index = pushStyle(style)
    try {
        block()
    } finally {
        pop(index)
    }
}

/** Push a string annotation for the duration of [block] then pop it. */
private inline fun AnnotatedString.Builder.withAnnotationScope(
    tag: String,
    annotation: String,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val index = pushStringAnnotation(tag, annotation)
    try {
        block()
    } finally {
        pop(index)
    }
}

private fun headingStyle(level: UByte): TextStyle {
    return when (level.toInt()) {
        1 -> TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
        2 -> TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
        3 -> TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        4 -> TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        5 -> TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        else -> TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun shortEntity(value: String): String {
    if (value.length <= 12) return value
    return "${value.take(8)}…${value.takeLast(4)}"
}

// ---------------------------------------------------------------------------
// Block builders
// ---------------------------------------------------------------------------

@Composable
private fun MediaGroupBlock(urls: List<String>, kind: MediaKind) {
    when (kind) {
        MediaKind.Image -> {
            val nonEmpty = urls.filter { it.isNotEmpty() }
            if (nonEmpty.isNotEmpty()) {
                NostrMediaGrid(imageUrls = nonEmpty)
            }
        }
        MediaKind.Video, MediaKind.Audio -> {
            val first = urls.firstOrNull()
            if (first != null) {
                MediaRow(url = first, isAudio = kind == MediaKind.Audio)
            }
        }
    }
}

@Composable
private fun MediaRow(url: String, isAudio: Boolean) {
    val renderer = LocalNostrContentRenderer.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(renderer.codeBackgroundColor)
            .clickable { renderer.callbacks.onLinkTap(url) }
            .padding(12.dp),
    ) {
        Icon(
            imageVector = if (isAudio) Icons.Filled.VolumeUp else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = renderer.linkColor,
        )
        Text(
            text = url.substringAfterLast('/').ifEmpty { url },
            color = renderer.secondaryTextColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EventRefBlock(
    uri: WireNostrUri,
    quoteCardProvider: ((WireNostrUri) -> NostrQuoteCardModel?)?,
) {
    // Variant selection mirrors SwiftUI: provider missing → .Collapsed,
    // provider hit → .Rich, provider miss → .Missing.
    val providedModel = quoteCardProvider?.invoke(uri)
    val variant: NostrQuoteCardVariant
    val model: NostrQuoteCardModel
    when {
        quoteCardProvider == null -> {
            variant = NostrQuoteCardVariant.Collapsed
            model = NostrQuoteCardModel(id = uri.primaryId, unresolvedUri = uri.uri)
        }
        providedModel != null -> {
            variant = NostrQuoteCardVariant.Rich
            model = providedModel
        }
        else -> {
            variant = NostrQuoteCardVariant.Missing
            model = NostrQuoteCardModel(id = uri.primaryId, unresolvedUri = uri.uri)
        }
    }
    val renderer = LocalNostrContentRenderer.current
    NostrQuoteCard(
        model = model,
        variant = variant,
        onTap = { renderer.callbacks.onEventRefTap(uri.primaryId) },
    )
}

@Composable
private fun CodeBlockBlock(info: String?, body: String) {
    val renderer = LocalNostrContentRenderer.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(renderer.codeBackgroundColor)
            .padding(10.dp),
    ) {
        if (!info.isNullOrEmpty()) {
            Text(
                text = info,
                color = renderer.secondaryTextColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = body,
            color = renderer.textColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BlockQuoteBlock(
    children: List<UInt>,
    tree: ContentTreeWire,
    textStyle: TextStyle,
    mentionLabel: (WireNostrUri) -> String,
) {
    val renderer = LocalNostrContentRenderer.current
    val annotated = buildAnnotatedString {
        for (child in children) {
            appendInline(child, tree, renderer, mentionLabel)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(renderer.quoteBorderColor),
        )
        Text(
            text = annotated,
            color = renderer.secondaryTextColor,
            style = textStyle.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ListBlock(
    orderedStart: ULong?,
    items: List<List<UInt>>,
    tree: ContentTreeWire,
    textStyle: TextStyle,
    mentionLabel: (WireNostrUri) -> String,
) {
    val renderer = LocalNostrContentRenderer.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { offset, children ->
            val annotated = buildAnnotatedString {
                for (child in children) {
                    appendInline(child, tree, renderer, mentionLabel)
                }
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = marker(orderedStart, offset),
                    color = renderer.secondaryTextColor,
                    style = textStyle,
                )
                Text(
                    text = annotated,
                    color = renderer.textColor,
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun marker(orderedStart: ULong?, offset: Int): String {
    if (orderedStart != null) {
        return "${orderedStart + offset.toULong()}."
    }
    return "•"
}

@Composable
private fun RuleBlock() {
    val renderer = LocalNostrContentRenderer.current
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(renderer.quoteBorderColor),
    )
}

@Composable
private fun ImageBlock(alt: String, @Suppress("UNUSED_PARAMETER") title: String?, src: String?) {
    val renderer = LocalNostrContentRenderer.current
    if (!src.isNullOrEmpty()) {
        // `title` is reserved for a future accessibility label parity with the
        // SwiftUI implementation; the media grid currently only takes URLs.
        NostrMediaGrid(imageUrls = listOf(src))
        return
    }
    Text(
        text = if (alt.isEmpty()) "[image]" else "[$alt]",
        color = renderer.placeholderColor,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PlaceholderChip(reason: PlaceholderReason) {
    val renderer = LocalNostrContentRenderer.current
    val (label, icon) = when (reason) {
        PlaceholderReason.DepthLimit -> "Nested content collapsed" to Icons.Filled.ExpandMore
        PlaceholderReason.UnresolvedUri -> "Unresolved reference" to Icons.Filled.HelpOutline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(renderer.codeBackgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = renderer.placeholderColor)
        Text(
            text = label,
            color = renderer.placeholderColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
