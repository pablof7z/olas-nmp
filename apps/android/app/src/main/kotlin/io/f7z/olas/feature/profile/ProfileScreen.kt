package io.f7z.olas.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.ui.theme.OlasColors

@Composable
fun ProfileScreen(navController: NavController, pubkey: String?) {
    val vm: ProfileViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(pubkey) as T
        },
        key = "profile_$pubkey",
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isOwnProfile = pubkey == null

    when {
        state.isLoading && state.profile == null -> {
            Box(
                modifier = Modifier.fillMaxSize().background(OlasColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = OlasColors.Text1)
            }
        }
        state.profile == null -> {
            Box(
                modifier = Modifier.fillMaxSize().background(OlasColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Profile not found", color = OlasColors.Text2, fontSize = 17.sp)
            }
        }
        else -> {
            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                modifier              = Modifier.fillMaxSize().background(OlasColors.Background),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement   = Arrangement.spacedBy(1.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ProfileHeader(
                        profile        = state.profile!!,
                        isOwnProfile   = isOwnProfile,
                        isFollowing    = state.isFollowing,
                        followerCount  = 0,
                        followingCount = 0,
                        onFollow       = { vm.toggleFollow() },
                        onZap          = {},
                        onEdit         = { navController.navigate("profile_edit") },
                    )
                }
                items(state.posts, key = { it.id }) { post ->
                    ProfileGridCell(post = post)
                }
                if (state.posts.isEmpty() && !state.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier              = Modifier.fillMaxWidth().padding(top = 60.dp),
                            horizontalAlignment   = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.GridOn,
                                contentDescription = null,
                                tint               = OlasColors.Text3,
                                modifier           = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No posts yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = OlasColors.Text1)
                            Spacer(Modifier.height(4.dp))
                            Text("Share your first photo", fontSize = 14.sp, color = OlasColors.Text2)
                        }
                    }
                }
            }
        }
    }
}
