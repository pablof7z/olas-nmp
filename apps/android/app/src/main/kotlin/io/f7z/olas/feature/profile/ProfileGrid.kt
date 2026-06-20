package io.f7z.olas.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun ProfileGridCell(post: PhotoPost, onTap: () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RectangleShape)
            .background(OlasColors.Surface)
            .clickable { onTap() },
    ) {
        val thumbnailUrl = post.images.firstOrNull()?.url
        if (thumbnailUrl != null) {
            AsyncImage(
                model              = thumbnailUrl,
                contentDescription = "Post thumbnail",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
        }
    }
}
