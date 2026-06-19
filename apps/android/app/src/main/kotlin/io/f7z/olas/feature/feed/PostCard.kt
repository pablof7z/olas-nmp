package io.f7z.olas.feature.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.ui.theme.OlasColors
import org.nmp.registry.LocalNostrProfileHost

@Composable
fun PostCard(
    post: PhotoPost,
    onImageTap: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLiked by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    val profileHost = LocalNostrProfileHost.current
    val resolvedProfile = profileHost?.profileForPubkey(post.authorPubkey)
    val authorDisplay = resolvedProfile?.display ?: post.authorName ?: post.authorPubkey.take(8)

    Column(modifier = modifier.fillMaxWidth().background(OlasColors.Background)) {
        // Header
        PostHeader(post = post, onOverflow = {})

        // Image(s) — full bleed, aspect ratio capped at 4:5
        val firstImage = post.images.firstOrNull()
        if (firstImage != null) {
            val nativeRatio = if (firstImage.width != null && firstImage.height != null && firstImage.height > 0)
                firstImage.width.toFloat() / firstImage.height.toFloat()
            else
                1f
            // Cap portrait ratio at 4:5 (0.8), landscape at 3:2 (1.5)
            val displayRatio = nativeRatio.coerceIn(0.8f, 1.5f)

            if (post.images.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(displayRatio)
                        .background(OlasColors.Surface),
                ) {
                    AsyncImage(
                        model              = firstImage.url,
                        contentDescription = firstImage.alt ?: "Photo",
                        modifier           = Modifier
                            .fillMaxWidth()
                            .aspectRatio(displayRatio),
                        contentScale       = ContentScale.Crop,
                    )
                }
            } else {
                // Carousel (HorizontalPager) with dot indicator
                CarouselImages(images = post.images, aspectRatio = displayRatio, onTap = onImageTap)
            }
        }

        // Action row
        PostActions(
            isLiked      = isLiked,
            isBookmarked = isBookmarked,
            onLike       = { isLiked = !isLiked },
            onComment    = {},
            onZap        = {
                // Build the zap action JSON in Rust (sats→msats conversion is Rust's responsibility).
                val zapJson = NMPBridge.buildZapActionJson(post.id, 21L)
                if (zapJson != null) NMPBridge.dispatchAction("nmp.zap", zapJson)
            },
            onShare      = {},
            onBookmark   = { isBookmarked = !isBookmarked },
        )

        // Reaction count
        if (post.reactionCount > 0) {
            Text(
                text     = "${post.reactionCount} reactions",
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color    = OlasColors.Text1,
            )
        }

        // Caption line: bold username + caption inline
        if (post.caption.isNotBlank()) {
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)) {
                    append(authorDisplay)
                }
                append("  ")
                append(post.caption)
            }
            Text(
                text     = annotated,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 14.sp,
                color    = OlasColors.Text1,
                maxLines = 2,
            )
        }

        // Comments count
        if (post.commentCount > 0) {
            Text(
                text     = "View ${post.commentCount} comments",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 14.sp,
                color    = OlasColors.Text2,
            )
        }

        // Timestamp
        Text(
            text     = relativeTime(post.createdAt),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            fontSize = 12.sp,
            color    = OlasColors.Text3,
        )

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = OlasColors.Border, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselImages(
    images: List<io.f7z.olas.core.ImageMeta>,
    aspectRatio: Float,
    onTap: (String) -> Unit,
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { images.size }
    Box {
        androidx.compose.foundation.pager.HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(OlasColors.Surface),
            ) {
                AsyncImage(
                    model              = images[page].url,
                    contentDescription = images[page].alt ?: "Photo ${page + 1}",
                    modifier           = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                    contentScale       = ContentScale.Crop,
                )
            }
        }
        // Dot indicator
        if (images.size > 1) {
            Row(
                modifier         = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(images.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) OlasColors.Text1 else OlasColors.Text2.copy(alpha = 0.5f)),
                    )
                    if (i < images.size - 1) Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}
