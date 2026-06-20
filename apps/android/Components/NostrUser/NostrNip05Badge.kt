package org.nmp.registry

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * NIP-05 verified identity badge — verified icon + identifier string.
 *
 * Renders nothing when `profile.nip05` is null or empty.
 *
 * Depends on `compose/user-avatar` for [ProfileWire].
 */
@Composable
fun NostrNip05Badge(
    profile: ProfileWire,
    modifier: Modifier = Modifier,
) {
    val nip05 = profile.nip05?.takeIf { it.isNotEmpty() } ?: return
    NostrNip05Badge(nip05 = nip05, modifier = modifier)
}

@Composable
fun NostrNip05Badge(
    nip05: String,
    modifier: Modifier = Modifier,
) {
    // `_@domain` is NIP-05 shorthand: the domain itself is the identity. Display just the domain.
    val displayText = if (nip05.startsWith("_@")) nip05.removePrefix("_@") else nip05
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Verified: $displayText"
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
