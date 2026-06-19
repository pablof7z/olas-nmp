package io.f7z.olas.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.ui.theme.OlasColors
import org.nmp.registry.NostrAvatar
import org.nmp.registry.NostrProfileName

@Composable
fun PostHeader(post: PhotoPost, onOverflow: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // NMP avatar — uses profile host if available, falls back to identicon
        NostrAvatar(
            pubkey    = post.authorPubkey,
            avatarUrl = post.authorAvatar,
            size      = 32.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // NMP profile name — uses profile host if available, falls back to
            // the authorName already embedded in the post
            NostrProfileName(
                pubkey = post.authorPubkey,
                style  = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color  = OlasColors.Text1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text     = relativeTime(post.createdAt),
            fontSize = 13.sp,
            color    = OlasColors.Text2,
        )
        IconButton(onClick = onOverflow, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector        = Icons.Filled.MoreHoriz,
                contentDescription = "More options",
                tint               = OlasColors.Text2,
            )
        }
    }
}

/** Human-relative timestamp: "2h", "just now", "Jun 12". */
fun relativeTime(epochSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000L
    val diff = now - epochSeconds
    return when {
        diff < 60            -> "just now"
        diff < 3600          -> "${diff / 60}m"
        diff < 86400         -> "${diff / 3600}h"
        diff < 86400 * 7     -> "${diff / 86400}d"
        else                 -> {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = epochSeconds * 1000L
            val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
        }
    }
}
