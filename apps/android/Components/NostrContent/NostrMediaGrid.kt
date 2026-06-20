// Requires: compose-ui, compose-foundation, compose-material3,
// androidx.compose.material:material-icons-extended (for `BrokenImage`),
// io.coil-kt:coil-compose (>= 2.x). Kotlin 1.9+.
//
// Compose mirror of the SwiftUI `NostrMediaGrid`. Photo-style adaptive grid:
//
//   - 1 image  → full-width 16:9
//   - 2 images → side-by-side, equal halves
//   - 3 images → one large on the left, two stacked on the right
//   - 4+       → 2×2 grid; last visible cell carries a `+N more` overlay
//
// Tapping a cell calls `LocalNostrContentRenderer.current.callbacks.onImageTap`
// by default. Apps that want different routing can supply a `tapHandler`.
//
// Depends on `compose/content-core`.

package nmp.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

@Composable
public fun NostrMediaGrid(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    cornerRadius: Dp = 10.dp,
    tapHandler: ((String) -> Unit)? = null,
) {
    if (imageUrls.isEmpty()) return
    val renderer = LocalNostrContentRenderer.current
    val onTap: (String) -> Unit = tapHandler ?: renderer.callbacks.onImageTap

    when (imageUrls.size) {
        1 -> SingleLayout(
            url = imageUrls[0],
            aspectRatio = aspectRatio,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = modifier,
        )
        2 -> DoubleLayout(
            left = imageUrls[0],
            right = imageUrls[1],
            aspectRatio = aspectRatio,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = modifier,
        )
        3 -> TripleLayout(
            primary = imageUrls[0],
            secondary = imageUrls[1],
            tertiary = imageUrls[2],
            aspectRatio = aspectRatio,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = modifier,
        )
        else -> QuadLayout(
            urls = imageUrls,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = modifier,
        )
    }
}

@Composable
private fun SingleLayout(
    url: String,
    aspectRatio: Float,
    cornerRadius: Dp,
    onTap: (String) -> Unit,
    modifier: Modifier,
) {
    MediaCell(
        url = url,
        overlay = null,
        cornerRadius = cornerRadius,
        onTap = onTap,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
    )
}

@Composable
private fun DoubleLayout(
    left: String,
    right: String,
    aspectRatio: Float,
    cornerRadius: Dp,
    onTap: (String) -> Unit,
    modifier: Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
    ) {
        MediaCell(
            url = left,
            overlay = null,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
        MediaCell(
            url = right,
            overlay = null,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
    }
}

@Composable
private fun TripleLayout(
    primary: String,
    secondary: String,
    tertiary: String,
    aspectRatio: Float,
    cornerRadius: Dp,
    onTap: (String) -> Unit,
    modifier: Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
    ) {
        MediaCell(
            url = primary,
            overlay = null,
            cornerRadius = cornerRadius,
            onTap = onTap,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            MediaCell(
                url = secondary,
                overlay = null,
                cornerRadius = cornerRadius,
                onTap = onTap,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            MediaCell(
                url = tertiary,
                overlay = null,
                cornerRadius = cornerRadius,
                onTap = onTap,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun QuadLayout(
    urls: List<String>,
    cornerRadius: Dp,
    onTap: (String) -> Unit,
    modifier: Modifier,
) {
    val visible = urls.take(4)
    val extra = urls.size - visible.size
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            MediaCell(
                url = visible[0],
                overlay = null,
                cornerRadius = cornerRadius,
                onTap = onTap,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            MediaCell(
                url = visible[1],
                overlay = null,
                cornerRadius = cornerRadius,
                onTap = onTap,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            MediaCell(
                url = visible[2],
                overlay = null,
                cornerRadius = cornerRadius,
                onTap = onTap,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            MediaCell(
                url = visible[3],
                overlay = if (extra > 0) "+$extra" else null,
                cornerRadius = cornerRadius,
                onTap = onTap,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MediaCell(
    url: String,
    overlay: String?,
    cornerRadius: Dp,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalNostrContentRenderer.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onTap(url) }
            .semantics { contentDescription = "Image" },
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(renderer.codeBackgroundColor),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            },
            error = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(renderer.codeBackgroundColor),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = renderer.placeholderColor,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (overlay != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = overlay,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
