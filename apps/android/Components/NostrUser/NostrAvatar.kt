package org.nmp.registry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.util.UUID

/**
 * Circular avatar for a Nostr pubkey. Shows the profile picture when the
 * host projection has it; falls back to a deterministic identicon derived
 * from `pubkey`.
 *
 * Replace [SubcomposeAsyncImage] with Glide/Picasso/custom if you already
 * have an image loader — the identicon fallback is self-contained.
 *
 * Depends on `compose/user-avatar` for [ProfileWire] and [NostrProfileHost].
 */
@Composable
fun NostrAvatar(
    pubkey: String,
    avatarUrl: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    consumerId: String? = null,
) {
    val profileHost = LocalNostrProfileHost.current
    val resolvedConsumerId = remember(pubkey, consumerId) {
        consumerId ?: "nostr-avatar.${UUID.randomUUID()}"
    }
    val resolvedAvatarUrl = avatarUrl ?: profileHost?.profileForPubkey(pubkey)?.avatarUrl

    DisposableEffect(pubkey, resolvedConsumerId) {
        profileHost?.claimProfile(pubkey, resolvedConsumerId)
        onDispose {
            profileHost?.releaseProfile(pubkey, resolvedConsumerId)
        }
    }

    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .clearAndSetSemantics {}

    if (!resolvedAvatarUrl.isNullOrEmpty()) {
        SubcomposeAsyncImage(
            model = resolvedAvatarUrl,
            contentDescription = null,
            modifier = baseModifier,
            error = { NostrIdenticonBox(pubkey = pubkey, size = size) },
            loading = { NostrIdenticonBox(pubkey = pubkey, size = size) },
        )
    } else {
        NostrIdenticonBox(pubkey = pubkey, size = size, modifier = baseModifier)
    }
}

/** Convenience overload accepting a [ProfileWire]. */
@Composable
fun NostrAvatar(
    profile: ProfileWire,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) = NostrAvatar(
    pubkey = profile.pubkey,
    avatarUrl = profile.avatarUrl,
    size = size,
    modifier = modifier,
)

// ── Identicon ────────────────────────────────────────────────────────────────

private val IDENTICON_PALETTE = listOf(
    Color(0xFF5C33CF),
    Color(0xFF1A87D1),
    Color(0xFF218C6A),
    Color(0xFFD1542E),
    Color(0xFFC12573),
    Color(0xFF00897B),
)

private fun identiconColor(pubkey: String): Color {
    val sum = pubkey.take(4).sumOf { it.code }
    return IDENTICON_PALETTE[sum % IDENTICON_PALETTE.size]
}

private fun identiconInitials(pubkey: String): String =
    pubkey.take(2).uppercase()

@Composable
private fun NostrIdenticonBox(pubkey: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(identiconColor(pubkey)),
    ) {
        Text(
            text = identiconInitials(pubkey),
            color = Color.White,
            fontSize = (size.value * 0.35f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
