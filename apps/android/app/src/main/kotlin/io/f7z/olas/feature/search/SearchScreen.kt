package io.f7z.olas.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.NostrEvent
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.navigation.Routes
import io.f7z.olas.ui.theme.OlasColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SearchScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var lastSearchQuery by remember { mutableStateOf<String?>(null) }
    val profileResults = remember { mutableStateListOf<OlasProfile>() }
    val postResults = remember { mutableStateListOf<PhotoPost>() }
    val tagResults = remember { mutableStateListOf<String>() }
    val json = remember { Json { ignoreUnknownKeys = true } }

    LaunchedEffect(Unit) {
        snapshotFlow { query.trim() }
            .debounce(500)
            .distinctUntilChanged()
            .collect { nextQuery ->
                lastSearchQuery?.let { previous ->
                    if (previous != nextQuery) NMPBridge.closeSearchFeed(previous)
                }
                profileResults.clear()
                postResults.clear()
                tagResults.clear()
                if (nextQuery.isBlank()) {
                    isSearching = false
                    lastSearchQuery = null
                } else {
                    isSearching = true
                    lastSearchQuery = nextQuery
                    NMPBridge.openSearchFeed(nextQuery)
                }
            }
    }

    LaunchedEffect(Unit) {
        NMPBridge.nostrEvents.collect { raw ->
            val currentQuery = query.trim()
            if (currentQuery.isBlank()) return@collect
            val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                ?: return@collect
            when (event.kind) {
                0 -> {
                    val profile = NMPBridge.profileJson(raw)
                        ?.let { runCatching { json.decodeFromString<OlasProfile>(it) }.getOrNull() }
                        ?: return@collect
                    if (profile.matches(currentQuery) && profileResults.none { it.pubkey == profile.pubkey }) {
                        profileResults.add(profile)
                    }
                }
                20 -> {
                    val post = NMPBridge.photoPostJson(raw, io.f7z.olas.core.FeedMode.NETWORK)
                        ?.let { runCatching { json.decodeFromString<PhotoPost>(it) }.getOrNull() }
                        ?: return@collect
                    if (post.matches(currentQuery) && postResults.none { it.id == post.id }) {
                        postResults.add(post)
                    }
                    post.hashtags
                        .filter { it.contains(currentQuery, ignoreCase = true) }
                        .filterNot { tagResults.contains(it) }
                        .forEach { tagResults.add(it) }
                }
            }
            isSearching = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { lastSearchQuery?.let { NMPBridge.closeSearchFeed(it) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OlasColors.Background),
    ) {
        SearchBar(
            query         = query,
            onQueryChange = { query = it },
            onSearch      = { active = false },
            active        = active,
            onActiveChange = { active = it },
            modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder   = { Text("Search people, photos, tags", color = OlasColors.Text3) },
            colors        = SearchBarDefaults.colors(
                containerColor = OlasColors.Surface,
                dividerColor   = OlasColors.Border,
            ),
        ) {
            SearchResults(
                query = query,
                isSearching = isSearching,
                profiles = profileResults,
                posts = postResults,
                tags = tagResults,
                navController = navController,
            )
        }

        if (query.isBlank()) {
            DiscoverScreen()
        } else if (!active) {
            SearchResults(
                query = query,
                isSearching = isSearching,
                profiles = profileResults,
                posts = postResults,
                tags = tagResults,
                navController = navController,
            )
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    isSearching: Boolean,
    profiles: List<OlasProfile>,
    posts: List<PhotoPost>,
    tags: List<String>,
    navController: NavController,
) {
    when {
        query.isBlank() -> Unit
        isSearching && profiles.isEmpty() && posts.isEmpty() && tags.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OlasColors.Text1)
            }
        }
        profiles.isEmpty() && posts.isEmpty() && tags.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results", color = OlasColors.Text2, fontSize = 16.sp)
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (profiles.isNotEmpty()) {
                    item { SectionHeader("People") }
                    items(profiles, key = { it.pubkey }) { profile ->
                        ProfileResultRow(profile) { navController.navigate(Routes.profile(profile.pubkey)) }
                    }
                }
                if (posts.isNotEmpty()) {
                    item { SectionHeader("Photos") }
                    items(posts, key = { it.id }) { post ->
                        PhotoResultRow(post) {
                            post.images.firstOrNull()?.url?.let { navController.navigate(Routes.fullscreenImage(it)) }
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    item { SectionHeader("Tags") }
                    items(tags, key = { it }) { tag ->
                        Text(
                            text     = "#$tag",
                            fontSize = 16.sp,
                            color    = OlasColors.Text1,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text       = title,
        fontSize   = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color      = OlasColors.Text3,
        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ProfileResultRow(profile: OlasProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model              = profile.picture,
            contentDescription = "Avatar of ${profile.displayName ?: profile.name ?: profile.pubkey.take(8)}",
            modifier           = Modifier.size(44.dp).clip(CircleShape).background(OlasColors.Surface2),
            contentScale       = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(profile.displayName ?: profile.name ?: profile.pubkey.take(8), fontSize = 16.sp, color = OlasColors.Text1)
            profile.nip05?.let { Text(it, fontSize = 13.sp, color = OlasColors.Text2) }
        }
    }
}

@Composable
private fun PhotoResultRow(post: PhotoPost, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model              = post.images.firstOrNull()?.url,
            contentDescription = post.images.firstOrNull()?.alt ?: "Photo",
            modifier           = Modifier.size(56.dp).background(OlasColors.Surface2),
            contentScale       = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(post.authorName ?: post.authorPubkey.take(8), fontSize = 15.sp, color = OlasColors.Text1)
            Text(post.caption.ifBlank { "Photo post" }, fontSize = 13.sp, color = OlasColors.Text2, maxLines = 2)
        }
    }
}

private fun OlasProfile.matches(query: String): Boolean {
    val q = query.lowercase()
    return listOfNotNull(name, displayName, about, nip05, pubkey).any { it.lowercase().contains(q) }
}

private fun PhotoPost.matches(query: String): Boolean {
    val q = query.lowercase()
    return caption.lowercase().contains(q) || hashtags.any { it.lowercase().contains(q) }
}
