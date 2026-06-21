package io.f7z.olas.feature.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image as FoundationImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.f7z.olas.core.ImageMeta
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.ui.components.BlurHashDecoder
import io.f7z.olas.ui.theme.OlasColors
import org.nmp.registry.LocalNostrProfileHost

@Composable
fun PostCard(
    post: PhotoPost,
    onImageTap: (url: String) -> Unit,
    reduceMotion: Boolean = false,
    onLike: ((PhotoPost) -> Unit)? = null,
    onBookmark: ((PhotoPost) -> Unit)? = null,
    onZap: ((PhotoPost) -> Unit)? = null,
    onShare: ((PhotoPost) -> Unit)? = null,
    onComment: ((PhotoPost) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isLiked by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    val profileHost = LocalNostrProfileHost.current

    LaunchedEffect(post.authorPubkey) {
        profileHost?.claimProfile(post.authorPubkey, "feed.${post.id}")
    }
    DisposableEffect(post.authorPubkey) {
        onDispose { profileHost?.releaseProfile(post.authorPubkey, "feed.${post.id}") }
    }

    val resolvedProfile = profileHost?.profileForPubkey(post.authorPubkey)
    val authorDisplay = resolvedProfile?.display ?: post.authorName ?: post.authorPubkey.take(8)

    Column(modifier = modifier.fillMaxWidth().background(OlasColors.Background)) {
        PostHeader(post = post, onOverflow = {})

        val firstImage = post.images.firstOrNull()
        if (firstImage != null) {
            val displayRatio = computeDisplayRatio(firstImage)

            if (post.images.size == 1) {
                PostImage(
                    image        = firstImage,
                    aspectRatio  = displayRatio,
                    reduceMotion = reduceMotion,
                    onClick      = { onImageTap(firstImage.url) },
                )
            } else {
                CarouselImages(
                    images       = post.images,
                    aspectRatio  = displayRatio,
                    onTap        = onImageTap,
                    reduceMotion = reduceMotion,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        PostActions(
            isLiked      = isLiked,
            isBookmarked = isBookmarked,
            onLike       = {
                isLiked = !isLiked
                if (isLiked) onLike?.invoke(post)
            },
            onComment    = { onComment?.invoke(post) },
            onZap        = {
                onZap?.invoke(post) ?: run {
                    val zapJson = NMPBridge.buildZapActionJson(post.id, 21L)
                    if (zapJson != null) NMPBridge.dispatchAction("nmp.zap", zapJson)
                }
            },
            onShare      = { onShare?.invoke(post) },
            onBookmark   = {
                isBookmarked = !isBookmarked
                onBookmark?.invoke(post)
            },
        )

        if (post.reactionCount > 0) {
            Text(
                text       = "${post.reactionCount} reactions",
                modifier   = Modifier.padding(horizontal = 12.dp),
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = OlasColors.Text1,
            )
        }

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
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (post.commentCount > 0) {
            Text(
                text     = "View ${post.commentCount} comments",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 14.sp,
                color    = OlasColors.Text2,
            )
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = OlasColors.Border, thickness = 0.5.dp)
    }
}

// Single image with blurhash placeholder and tap-to-fullscreen support.

@Composable
private fun PostImage(
    image: ImageMeta,
    aspectRatio: Float,
    reduceMotion: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val blurBitmap: ImageBitmap? = remember(image.blurhash) {
        image.blurhash?.let { BlurHashDecoder.decode(it, 32, 32) }
    }
    val altLabel = image.alt ?: "Photo"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(OlasColors.Surface)
            .semantics { contentDescription = altLabel }
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        if (blurBitmap != null) {
            FoundationImage(
                bitmap            = blurBitmap,
                contentDescription = null,
                modifier          = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                contentScale      = ContentScale.Crop,
            )
        }

        val request = ImageRequest.Builder(context)
            .data(image.url)
            .crossfade(if (reduceMotion) 0 else 300)
            .build()

        AsyncImage(
            model              = request,
            contentDescription = null,
            modifier           = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            contentScale       = ContentScale.Crop,
        )
    }
}

// Carousel with per-page blurhash placeholders and tap-to-fullscreen support.

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselImages(
    images: List<ImageMeta>,
    aspectRatio: Float,
    onTap: (String) -> Unit,
    reduceMotion: Boolean,
) {
    val context = LocalContext.current
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { images.size }
    Box {
        androidx.compose.foundation.pager.HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
        ) { page ->
            val image = images[page]
            val blurBitmap: ImageBitmap? = remember(image.blurhash) {
                image.blurhash?.let { BlurHashDecoder.decode(it, 32, 32) }
            }
            val altLabel = image.alt ?: "Photo ${page + 1}"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(OlasColors.Surface)
                    .semantics { contentDescription = altLabel }
                    .clickable { onTap(image.url) },
            ) {
                if (blurBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap             = blurBitmap,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                        contentScale       = ContentScale.Crop,
                    )
                }
                val request = ImageRequest.Builder(context)
                    .data(image.url)
                    .crossfade(if (reduceMotion) 0 else 300)
                    .build()
                AsyncImage(
                    model              = request,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                    contentScale       = ContentScale.Crop,
                )
            }
        }

        if (images.size > 1) {
            Row(
                modifier          = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(images.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) OlasColors.Text1
                                else OlasColors.Text2.copy(alpha = 0.5f)
                            ),
                    )
                    if (i < images.size - 1) Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

// Compute display aspect ratio capped at 4:5 portrait / 3:2 landscape.
private fun computeDisplayRatio(image: ImageMeta): Float {
    val native = image.dimensions
        ?.takeIf { it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
        ?: 1f
    return native.coerceIn(0.8f, 1.5f)
}
