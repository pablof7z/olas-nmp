package io.f7z.olas.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.f7z.olas.feature.feed.relativeTime
import io.f7z.olas.ui.theme.OlasColors

enum class NotificationType { REACTION, COMMENT, MENTION, FOLLOW, REPOST, ZAP }

/** Flat data class kept for legacy callers; grouped variant is [GroupedNotificationItem]. */
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val actorName: String,
    val actorAvatar: String?,
    val body: String,
    val thumbnailUrl: String?,
    val createdAt: Long,
)

/** Grouped notification as returned by `olas_group_notifications_json`. */
data class GroupedNotificationItem(
    val groupId: String,
    val kind: String,            // "reaction" | "comment" | "mention" | "follow" | "repost" | "zap"
    val targetPostId: String?,
    val actorPubkeys: List<String>,
    val count: Int,
    val latestTs: Long,
    val zapSats: Long?,
)

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun GroupedNotificationItemRow(
    item: GroupedNotificationItem,
    profileCache: Map<String, Pair<String?, String?>> = emptyMap(), // pubkey → (displayName, avatarUrl)
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stacked actor avatars (up to 3) — rendered in reverse so actor[0] is on top
        Box(modifier = Modifier.width(stackedAvatarWidth(item.actorPubkeys.size))) {
            val actors = item.actorPubkeys.take(3)
            // Render highest index first so actor[0] (most recent) is drawn on top.
            actors.indices.reversed().forEach { i ->
                val pubkey = actors[i]
                val (_, avatarUrl) = profileCache[pubkey] ?: Pair(null, null)
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .offset(x = (i * 14).dp),
                ) {
                    AsyncImage(
                        model              = avatarUrl,
                        contentDescription = null,
                        modifier           = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(OlasColors.Surface),
                        contentScale       = ContentScale.Crop,
                    )
                    // type badge only on the first (top) avatar
                    if (i == 0) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(notificationBadgeColor(item.kind))
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(notificationEmoji(item.kind), fontSize = 7.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            val actorLabel = buildActorLabel(item, profileCache)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(actorLabel) }
                    append(" ")
                    append(notificationBody(item))
                },
                fontSize = 14.sp,
                color    = OlasColors.Text1,
                maxLines = 2,
            )
            Text(relativeTime(item.latestTs), fontSize = 12.sp, color = OlasColors.Text3)
        }
    }
}

private fun stackedAvatarWidth(count: Int): androidx.compose.ui.unit.Dp =
    (30 + (minOf(count, 3) - 1).coerceAtLeast(0) * 14).dp

private fun buildActorLabel(
    item: GroupedNotificationItem,
    profileCache: Map<String, Pair<String?, String?>>,
): String {
    val names = item.actorPubkeys.take(2).map { pubkey ->
        profileCache[pubkey]?.first ?: pubkey.take(8)
    }
    val others = item.count - names.size
    val base = names.joinToString(", ")
    return if (others > 0) "$base +$others others" else base
}

private fun notificationBody(item: GroupedNotificationItem): String = when (item.kind) {
    "reaction" -> "reacted to your photo"
    "comment"  -> "commented on your photo"
    "mention"  -> "mentioned you"
    "follow"   -> "followed you"
    "repost"   -> "reposted your photo"
    "zap"      -> if ((item.zapSats ?: 0L) > 0L) "zapped ⚡ ${item.zapSats} sats" else "zapped your photo"
    else       -> "interacted with you"
}

private fun notificationEmoji(kind: String) = when (kind) {
    "reaction" -> "❤️"
    "comment"  -> "💬"
    "mention"  -> "@"
    "follow"   -> "➕"
    "repost"   -> "↗"
    "zap"      -> "⚡"
    else       -> "🔔"
}

private fun notificationBadgeColor(kind: String) = when (kind) {
    "reaction" -> OlasColors.Heart
    "zap"      -> OlasColors.Zap
    else       -> OlasColors.Surface2
}

// Keep legacy row for any callers that haven't migrated yet.
@Composable
fun NotificationItemRow(item: NotificationItem) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                    .background(notificationBadgeColor(item.type.name.lowercase()))
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Text(notificationEmoji(item.type.name.lowercase()), fontSize = 8.sp)
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
