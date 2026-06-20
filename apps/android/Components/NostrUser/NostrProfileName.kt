package org.nmp.registry

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.util.UUID

/**
 * Inline display-name text for a Nostr profile.
 *
 * Two rendering modes:
 *   • [NostrProfileName] `(profile = …)` — caller already holds a
 *     [ProfileWire] (static, no claiming). Renders `profile.display`.
 *   • [NostrProfileName] `(pubkey = …)` — *self-claiming*. The component owns
 *     claiming the kind:0 it needs: it claims the author's profile from the
 *     [NostrProfileHost] on composition, reads the resolved projection
 *     reactively, and releases on disposal. This mirrors [NostrAvatar]'s
 *     claim/release lifecycle exactly.
 *
 * Display always comes from a Rust-formatted source — `displayName` when the
 * kind:0 has resolved, else the Rust-truncated `npubShort` (never reformat in
 * Kotlin, never raw hex). In the self-claiming mode, until the host has any
 * profile for the pubkey the component renders nothing rather than synthesize a
 * Kotlin-side abbreviation.
 *
 * Depends on `compose/user-avatar` for [ProfileWire] and [NostrProfileHost].
 */
@Composable
fun NostrProfileName(
    profile: ProfileWire,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
    color: Color = Color.Unspecified,
) {
    val label = profile.display
    Text(
        text = label,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = "Display name: $label" },
    )
}

/**
 * Self-claiming display-name text keyed by `pubkey`.
 *
 * Claims the author's kind:0 from [NostrProfileHost] on composition (via
 * [DisposableEffect], like [NostrAvatar]), reads the resolved [ProfileWire]
 * reactively, and releases on disposal. The presentation layer owns claiming —
 * no event triggers a kernel kind:0 fetch of the author.
 *
 * Renders nothing until the host resolves a profile for `pubkey`, so the
 * fallback is always the Rust-formatted `npubShort`, never a Kotlin-side hex
 * abbreviation.
 */
@Composable
fun NostrProfileName(
    pubkey: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold),
    color: Color = Color.Unspecified,
    consumerId: String? = null,
) {
    val profileHost = LocalNostrProfileHost.current
    val resolvedConsumerId = remember(pubkey, consumerId) {
        consumerId ?: "nostr-profile-name.${UUID.randomUUID()}"
    }

    DisposableEffect(pubkey, resolvedConsumerId) {
        profileHost?.claimProfile(pubkey, resolvedConsumerId)
        onDispose {
            profileHost?.releaseProfile(pubkey, resolvedConsumerId)
        }
    }

    // Read reactively from the host: recomposes when the kind:0 resolves.
    val resolved = profileHost?.profileForPubkey(pubkey) ?: return
    NostrProfileName(
        profile = resolved,
        modifier = modifier,
        style = style,
        color = color,
    )
}
