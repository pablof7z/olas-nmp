package io.f7z.olas.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.f7z.olas.navigation.Routes
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        when {
            state.isLoading && state.profile == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = OlasColors.Text1,
                )
            }
            state.profile == null -> {
                Text(
                    text     = "Profile not found",
                    modifier = Modifier.align(Alignment.Center),
                    color    = OlasColors.Text2,
                    fontSize = 17.sp,
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        ProfileHeader(
                            profile       = state.profile!!,
                            isOwnProfile  = isOwnProfile,
                            isFollowing   = state.isFollowing,
                            followerCount = 0,
                            followingCount = 0,
                            onFollow      = { vm.toggleFollow() },
                            onZap         = {},
                            onEdit        = { navController.navigate("profile_edit") },
                        )
                    }
                    item {
                        ProfileGrid(
                            posts     = state.posts,
                            onPostTap = {},
                        )
                    }
                }
            }
        }
    }
}
