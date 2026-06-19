package io.f7z.olas.feature.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun PostActions(
    isLiked: Boolean,
    isBookmarked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onZap: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Like
        LikeButton(isLiked = isLiked, onClick = onLike)
        // Comment
        IconButton(onClick = onComment, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector        = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Comment",
                tint               = OlasColors.Text1,
                modifier           = Modifier.size(24.dp),
            )
        }
        // Zap
        ZapButton(onClick = onZap)

        // Share
        IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector        = Icons.Filled.Share,
                contentDescription = "Share",
                tint               = OlasColors.Text1,
                modifier           = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // Bookmark
        IconButton(onClick = onBookmark, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector        = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                tint               = OlasColors.Text1,
                modifier           = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun LikeButton(isLiked: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "like_scale",
    )
    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector        = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Liked" else "Like",
            tint               = if (isLiked) OlasColors.Heart else OlasColors.Text1,
            modifier           = Modifier.size(24.dp).scale(scale),
        )
    }
}

@Composable
private fun ZapButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        // Bolt icon approximation using a text character (Material bolt icon)
        androidx.compose.material3.Text(
            text     = "⚡",
            fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
            color    = OlasColors.Zap,
        )
    }
}
