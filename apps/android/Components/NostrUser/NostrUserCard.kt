package org.nmp.registry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compact author header: avatar, display name, and optional NIP-05 badge.
 *
 * The most common pattern in note feeds and thread views. Tap routes
 * through [onTap] so this component can be used in any navigation stack.
 *
 * Depends on `compose/user-avatar`, `compose/user-name`, `compose/user-nip05`.
 */
@Composable
fun NostrUserCard(
    profile: ProfileWire,
    avatarSize: Dp = 40.dp,
    onTap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tapModifier = if (onTap != null) {
        modifier.clickable { onTap(profile.pubkey) }
    } else {
        modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = tapModifier.semantics(mergeDescendants = true) {
            contentDescription = "${profile.display}, profile"
        },
    ) {
        NostrAvatar(profile = profile, size = avatarSize)
        Spacer(Modifier.width(10.dp))
        Column {
            NostrProfileName(profile = profile)
            NostrNip05Badge(profile = profile)
        }
    }
}
