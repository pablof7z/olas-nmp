// Requires: compose-ui, compose-foundation, compose-material3,
// io.coil-kt:coil-compose (>= 2.x). Kotlin 1.9+.
//
// Compose mirror of the SwiftUI `NostrMentionChip`. Renders `@displayName`
// alongside an optional avatar, falling back to a deterministic identicon
// when no URL is provided.
//
// Depends on `compose/content-core` for `LocalNostrContentRenderer` (colors
// + callbacks) and `NostrIdenticon`.

package nmp.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

@Composable
public fun NostrMentionChip(
    pubkey: String,
    displayName: String? = null,
    avatarUrl: String? = null,
    showsAvatar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalNostrContentRenderer.current
    val label = displayName?.takeIf { it.isNotEmpty() } ?: shortPubkey(pubkey)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clickable { renderer.callbacks.onMentionTap(pubkey) }
            .semantics { contentDescription = "Mention of $label" },
    ) {
        if (showsAvatar) {
            MentionAvatar(pubkey = pubkey, avatarUrl = avatarUrl)
        }
        Text(
            text = "@$label",
            color = renderer.mentionColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MentionAvatar(pubkey: String, avatarUrl: String?) {
    val size = 16.dp
    if (avatarUrl.isNullOrEmpty()) {
        MentionAvatarFallback(pubkey = pubkey)
        return
    }
    SubcomposeAsyncImage(
        model = avatarUrl,
        contentDescription = null,
        loading = { MentionAvatarFallback(pubkey = pubkey) },
        error = { MentionAvatarFallback(pubkey = pubkey) },
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
    )
}

@Composable
private fun MentionAvatarFallback(pubkey: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(NostrIdenticon.colorForPubkey(pubkey)),
    ) {
        Text(
            text = NostrIdenticon.initialsForPubkey(pubkey),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun shortPubkey(value: String): String {
    if (value.length <= 12) return value
    return "${value.take(8)}…${value.takeLast(4)}"
}
