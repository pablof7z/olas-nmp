// Requires: compose-ui, compose-foundation, compose-material3,
// androidx.compose.material:material-icons-extended (for ChevronRight,
// FormatQuote, WarningAmber), io.coil-kt:coil-compose (>= 2.x). Kotlin 1.9+.
//
// Compose mirror of the SwiftUI `NostrQuoteCard`. Reusable quote / embedded-
// event card with four stable rendering contracts:
//
//   - .Collapsed → single-line "View quote" affordance; expands on tap
//   - .Compact   → author + truncated content text, border only
//   - .Rich      → avatar + name + truncated content + optional media thumb
//   - .Missing   → "Content unavailable" placeholder with unresolvedUri
//
// The app is responsible for hydrating the model; the card only renders.
// Depends on `compose/content-core`.

package nmp.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

public enum class NostrQuoteCardVariant {
    /** Single-line `View quote` affordance; expands to `.Rich` on tap when wired. */
    Collapsed,

    /** Author + truncated content text, framed only by a border. */
    Compact,

    /** Author avatar + name + truncated content + optional media thumb. */
    Rich,

    /** "Content unavailable" placeholder; surfaces `unresolvedUri`. */
    Missing,
}

public data class NostrQuoteCardModel(
    val id: String,
    val unresolvedUri: String? = null,
    val authorPubkey: String? = null,
    val authorDisplayName: String? = null,
    val authorAvatarUrl: String? = null,
    val content: String = "",
    val mediaThumbnailUrl: String? = null,
    val createdAtDisplay: String? = null,
) {
    public companion object {
        public val Missing: NostrQuoteCardModel = NostrQuoteCardModel(id = "")
    }
}

@Composable
public fun NostrQuoteCard(
    model: NostrQuoteCardModel,
    modifier: Modifier = Modifier,
    variant: NostrQuoteCardVariant = NostrQuoteCardVariant.Rich,
    onTap: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
) {
    val renderer = LocalNostrContentRenderer.current
    val defaultTap: () -> Unit = { renderer.callbacks.onEventRefTap(model.id) }

    when (variant) {
        NostrQuoteCardVariant.Collapsed -> CollapsedBody(
            modifier = modifier,
            onTap = onExpand ?: onTap ?: defaultTap,
        )
        NostrQuoteCardVariant.Compact -> CompactBody(
            model = model,
            modifier = modifier,
            onTap = onTap ?: defaultTap,
        )
        NostrQuoteCardVariant.Rich -> RichBody(
            model = model,
            modifier = modifier,
            onTap = onTap ?: defaultTap,
        )
        NostrQuoteCardVariant.Missing -> MissingBody(
            model = model,
            modifier = modifier,
            onTap = onTap ?: defaultTap,
        )
    }
}

@Composable
private fun CollapsedBody(modifier: Modifier, onTap: () -> Unit) {
    val renderer = LocalNostrContentRenderer.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(renderer.quoteBackgroundColor)
            .border(1.5.dp, renderer.quoteBorderColor, RoundedCornerShape(8.dp))
            .clickable { onTap() }
            .padding(10.dp)
            .semantics { contentDescription = "View quoted post" },
    ) {
        Icon(
            imageVector = Icons.Filled.FormatQuote,
            contentDescription = null,
            tint = renderer.linkColor,
        )
        Text(
            text = "View quote",
            color = renderer.linkColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CompactBody(
    model: NostrQuoteCardModel,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    QuoteCardFrame(modifier = modifier, onTap = onTap) {
        val renderer = LocalNostrContentRenderer.current
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "@${authorLabel(model)}",
                color = renderer.textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Text(
                text = truncated(model.content, limit = 140),
                color = renderer.textColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RichBody(
    model: NostrQuoteCardModel,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val renderer = LocalNostrContentRenderer.current
    QuoteCardFrame(modifier = modifier, onTap = onTap) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuoteAvatar(model = model, size = 26.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${authorLabel(model)}",
                        color = renderer.textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val createdAt = model.createdAtDisplay
                    if (createdAt != null) {
                        Text(
                            text = createdAt,
                            color = renderer.secondaryTextColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = renderer.secondaryTextColor,
                )
            }
            if (model.content.isNotEmpty()) {
                Text(
                    text = truncated(model.content, limit = 240),
                    color = renderer.textColor,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val thumb = model.mediaThumbnailUrl
            if (thumb != null) {
                QuoteThumbnail(url = thumb)
            }
        }
    }
}

@Composable
private fun MissingBody(
    model: NostrQuoteCardModel,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val renderer = LocalNostrContentRenderer.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(renderer.quoteBackgroundColor)
            .border(1.5.dp, renderer.quoteBorderColor, RoundedCornerShape(8.dp))
            .clickable { onTap() }
            .semantics { contentDescription = "Open quoted post" }
            .padding(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = renderer.placeholderColor,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Content unavailable",
                color = renderer.textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            val uri = model.unresolvedUri
            if (!uri.isNullOrEmpty()) {
                // Compose has no built-in middle-ellipsis. Show a head+tail
                // window manually so the schema portion of the URI stays
                // visible alongside the trailing identifier.
                Text(
                    text = middleEllipsize(uri, maxLength = 48),
                    color = renderer.secondaryTextColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuoteCardFrame(
    modifier: Modifier,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val renderer = LocalNostrContentRenderer.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(renderer.quoteBackgroundColor)
            .border(1.5.dp, renderer.quoteBorderColor, RoundedCornerShape(8.dp))
            .clickable { onTap() }
            .padding(10.dp)
            .semantics { contentDescription = "Open quoted post" },
    ) {
        content()
    }
}

@Composable
private fun QuoteAvatar(model: NostrQuoteCardModel, size: Dp) {
    val identityKey = model.authorPubkey ?: model.id
    val avatarUrl = model.authorAvatarUrl
    if (avatarUrl.isNullOrEmpty()) {
        QuoteAvatarFallback(identityKey = identityKey, size = size)
        return
    }
    SubcomposeAsyncImage(
        model = avatarUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { QuoteAvatarFallback(identityKey = identityKey, size = size) },
        error = { QuoteAvatarFallback(identityKey = identityKey, size = size) },
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
    )
}

@Composable
private fun QuoteAvatarFallback(identityKey: String, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(NostrIdenticon.colorForPubkey(identityKey)),
    ) {
        Text(
            text = NostrIdenticon.initialsForPubkey(identityKey),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun QuoteThumbnail(url: String) {
    val renderer = LocalNostrContentRenderer.current
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(renderer.codeBackgroundColor),
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(renderer.codeBackgroundColor),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .clip(RoundedCornerShape(6.dp)),
    )
}

private fun authorLabel(model: NostrQuoteCardModel): String {
    val display = model.authorDisplayName
    if (!display.isNullOrEmpty()) return display
    val pubkey = model.authorPubkey
    if (!pubkey.isNullOrEmpty()) return shortPubkeyLocal(pubkey)
    return shortPubkeyLocal(model.id)
}

private fun shortPubkeyLocal(value: String): String {
    if (value.length <= 12) return value
    return "${value.take(8)}…${value.takeLast(4)}"
}

private fun truncated(value: String, limit: Int): String {
    val trimmed = value.trim()
    if (trimmed.length <= limit) return trimmed
    return "${trimmed.take(limit)}…"
}

private fun middleEllipsize(value: String, maxLength: Int): String {
    if (value.length <= maxLength) return value
    val half = (maxLength - 1) / 2
    return "${value.take(half)}…${value.takeLast(half)}"
}
