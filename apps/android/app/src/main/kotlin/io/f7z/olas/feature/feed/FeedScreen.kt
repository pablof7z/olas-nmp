package io.f7z.olas.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.core.WOT_GAP_NETWORK_FEED_LABEL
import io.f7z.olas.core.FeedMode
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(navController: NavController) {
    val vm: FeedViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        // Feed mode tabs
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
                text     = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Network", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        // WoT gap note: unfiltered feed
                        Text(WOT_GAP_NETWORK_FEED_LABEL, fontSize = 11.sp, color = OlasColors.Text3)
                    }
                },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = OlasColors.Text1,
                    )
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
                        state    = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.posts, key = { it.id }) { post ->
                            PostCard(
                                post       = post,
                                onImageTap = { /* fullscreen */ },
                            )
                        }
                        item {
                            // Load older trigger
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
