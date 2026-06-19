package io.f7z.olas.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun ProfileGrid(
    posts: List<PhotoPost>,
    onPostTap: (PhotoPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement   = Arrangement.spacedBy(1.dp),
        modifier              = modifier.fillMaxWidth(),
    ) {
        items(posts, key = { it.id }) { post ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(OlasColors.Surface)
                    .clickable { onPostTap(post) },
            ) {
                val thumbnailUrl = post.images.firstOrNull()?.url
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model              = thumbnailUrl,
                        contentDescription = "Post thumbnail",
                        modifier           = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale       = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
