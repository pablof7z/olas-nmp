package io.f7z.olas.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors

private enum class SearchResultTab(val label: String) {
    PEOPLE("People"),
    PHOTOS("Photos"),
    TAGS("Tags"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController) {
    val vm: SearchViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SearchResultTab.PEOPLE) }

    DisposableEffect(Unit) {
        onDispose { vm.closeSearch() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        SearchBar(
            query          = state.query,
            onQueryChange  = vm::onQueryChanged,
            onSearch       = { active = false },
            active         = active,
            onActiveChange = { active = it },
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder    = { Text("Search people, photos, tags", color = OlasColors.Text3) },
            colors         = SearchBarDefaults.colors(
                containerColor = OlasColors.Surface,
                dividerColor   = OlasColors.Border,
            ),
        ) {}

        if (state.query.isBlank()) {
            DiscoverScreen()
            return@Column
        }

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor   = OlasColors.Background,
            contentColor     = OlasColors.Text1,
        ) {
            SearchResultTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick  = { selectedTab = tab },
                    text     = { Text(tab.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                )
            }
        }

        if (state.isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OlasColors.Text1)
            }
            return@Column
        }

        when (selectedTab) {
            SearchResultTab.PEOPLE -> PeopleResults(state.profiles, navController)
            SearchResultTab.PHOTOS -> PhotoResults(state.posts)
            SearchResultTab.TAGS   -> TagResults(state.tags)
        }
    }
}

@Composable
private fun PeopleResults(profiles: List<OlasProfile>, navController: NavController) {
    if (profiles.isEmpty()) {
        EmptySearchState("No people found")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(profiles, key = { it.pubkey }) { profile ->
            ProfileRow(profile) { navController.navigate(Routes.profile(profile.pubkey)) }
            HorizontalDivider(color = OlasColors.Border)
        }
    }
}

@Composable
private fun ProfileRow(profile: OlasProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model              = profile.picture,
            contentDescription = null,
            modifier           = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OlasColors.Surface),
            contentScale       = ContentScale.Crop,
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text       = profile.displayName ?: profile.name ?: profile.pubkey.take(8),
                color      = OlasColors.Text1,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            profile.nip05?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = OlasColors.Text2, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PhotoResults(posts: List<PhotoPost>) {
    if (posts.isEmpty()) {
        EmptySearchState("No photos found")
        return
    }
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
        items(posts, key = { it.id }) { post ->
            AsyncImage(
                model              = post.images.firstOrNull()?.url,
                contentDescription = post.images.firstOrNull()?.alt ?: "Photo",
                modifier           = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(OlasColors.Surface),
                contentScale       = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun TagResults(tags: List<String>) {
    if (tags.isEmpty()) {
        EmptySearchState("No tags found")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags, key = { it }) { tag ->
            Text(
                text     = "#$tag",
                color    = OlasColors.Text1,
                fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            HorizontalDivider(color = OlasColors.Border)
        }
    }
}

@Composable
private fun EmptySearchState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = OlasColors.Text2, fontSize = 16.sp)
    }
}
