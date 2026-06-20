package io.f7z.olas.feature.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.core.FeedMode
import io.f7z.olas.ui.components.shimmer
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
fun FeedScreen(navController: NavController) {
    val vm: FeedViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mirror iOS: honour system Reduce Motion so shimmer + crossfade are suppressed.
    val reduceMotion = androidx.compose.ui.platform.LocalContext.current.let {
        val mgr = it.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val animScale = android.provider.Settings.Global.getFloat(
            it.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        animScale == 0f || mgr?.isEnabled == true && mgr.isTouchExplorationEnabled
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        TabRow(
            selectedTabIndex  = if (state.feedMode == FeedMode.FOLLOWING) 0 else 1,
            containerColor    = OlasColors.Background,
            contentColor      = OlasColors.Text1,
            indicator         = { tabPositions ->
                val index = if (state.feedMode == FeedMode.FOLLOWING) 0 else 1
                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                    color    = OlasColors.Text1,
                )
            },
        ) {
            Tab(
                selected = state.feedMode == FeedMode.FOLLOWING,
                onClick  = { vm.switchMode(FeedMode.FOLLOWING) },
                text     = { Text("Following", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
            )
            Tab(
                selected = state.feedMode == FeedMode.NETWORK,
                onClick  = { vm.switchMode(FeedMode.NETWORK) },
                text     = { Text("Network", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    // Shimmer skeleton replaces the bare CircularProgressIndicator
                    FeedSkeletonView(reduceMotion = reduceMotion)
                }
                state.posts.isEmpty() -> {
                    Text(
                        text     = "Nothing here yet",
                        modifier = Modifier.align(Alignment.Center),
                        color    = OlasColors.Text2,
                        fontSize = 17.sp,
                    )
                }
                else -> {
                    LazyColumn(
                        state          = listState,
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = if (state.hasNewPosts) PaddingValues(top = 48.dp)
                                         else PaddingValues(),
                    ) {
                        items(state.posts, key = { it.id }) { post ->
                            PostCard(
                                post         = post,
                                onImageTap   = { /* fullscreen */ },
                                reduceMotion = reduceMotion,
                                onLike       = vm::react,
                                onBookmark   = vm::bookmark,
                                onZap        = { vm.zap(it) },
                                onShare      = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://njump.me/${it.id}")
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share post"))
                                },
                                onComment    = {},
                            )
                        }
                        item {
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                vm.loadOlderPosts()
                            }
                        }
                    }
                }
            }

            // "N new posts" pill
            if (state.hasNewPosts) {
                val count = state.pendingPosts.size
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(OlasColors.Text1)
                        .clickable {
                            vm.showNewPosts()
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text       = "$count new post${if (count > 1) "s" else ""}",
                        color      = OlasColors.Background,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Skeleton screen
// ---------------------------------------------------------------------------

@Composable
private fun FeedSkeletonView(reduceMotion: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(3) {
            SkeletonCard(reduceMotion = reduceMotion)
            HorizontalDivider(color = OlasColors.Border, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun SkeletonCard(reduceMotion: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().background(OlasColors.Background)) {
        // Header row
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OlasColors.Surface2)
                    .shimmer(reduceMotion),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(14.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(OlasColors.Surface2)
                    .shimmer(reduceMotion),
            )
        }

        // Image placeholder — 4:5 matches feed default, layout reserved up-front
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .background(OlasColors.Surface2)
                .shimmer(reduceMotion),
        )

        // Caption lines
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .height(13.dp)
                    .width(120.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(OlasColors.Surface2)
                    .shimmer(reduceMotion),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .height(13.dp)
                    .width(200.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(OlasColors.Surface2)
                    .shimmer(reduceMotion),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
