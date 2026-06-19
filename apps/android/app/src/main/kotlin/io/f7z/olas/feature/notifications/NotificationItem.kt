package io.f7z.olas.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.feature.feed.relativeTime
import io.f7z.olas.ui.theme.OlasColors

enum class NotificationType { REACTION, COMMENT, MENTION, FOLLOW, REPOST, ZAP }

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val actorName: String,
    val actorAvatar: String?,
    val body: String,
    val thumbnailUrl: String?,
    val createdAt: Long,
)

@Composable
fun NotificationItemRow(item: NotificationItem) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar + type badge
        Box {
            AsyncImage(
                model              = item.actorAvatar,
                contentDescription = "Avatar of ${item.actorName}",
                modifier           = Modifier.size(36.dp).clip(CircleShape).background(OlasColors.Surface),
                contentScale       = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(notificationBadgeColor(item.type))
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Text(notificationEmoji(item.type), fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(item.actorName)
                    append(" ")
                    append(item.body)
                },
                fontSize  = 14.sp,
                color     = OlasColors.Text1,
                maxLines  = 2,
            )
            Text(relativeTime(item.createdAt), fontSize = 12.sp, color = OlasColors.Text3)
        }
        // Thumbnail
        if (item.thumbnailUrl != null) {
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model              = item.thumbnailUrl,
                contentDescription = "Post thumbnail",
                modifier           = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(OlasColors.Surface),
                contentScale       = ContentScale.Crop,
            )
        }
    }
}

private fun notificationEmoji(type: NotificationType) = when (type) {
    NotificationType.REACTION  -> "❤️"
    NotificationType.COMMENT   -> "💬"
    NotificationType.MENTION   -> "@"
    NotificationType.FOLLOW    -> "➕"
    NotificationType.REPOST    -> "↗"
    NotificationType.ZAP       -> "⚡"
}

private fun notificationBadgeColor(type: NotificationType) = when (type) {
    NotificationType.REACTION -> OlasColors.Heart
    NotificationType.ZAP      -> OlasColors.Zap
    else                      -> OlasColors.Surface2
}
