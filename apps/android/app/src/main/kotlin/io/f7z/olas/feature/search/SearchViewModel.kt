package io.f7z.olas.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.core.PhotoPost
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

data class SearchUiState(
    val query: String = "",
    val profiles: List<OlasProfile> = emptyList(),
    val posts: List<PhotoPost> = emptyList(),
    val tags: List<String> = emptyList(),
    val isSearching: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {
    private companion object {
        const val SEARCH_FEED_KEY = "olas.search"
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val json = Json { ignoreUnknownKeys = true }
    private val profilesByPubkey = linkedMapOf<String, OlasProfile>()
    private var activeQuery: String? = null

    init {
        observeSearchResults()
        queryFlow
            .debounce(500)
            .onEach { openSearch(it) }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        queryFlow.value = query
        if (query.isBlank()) {
            closeSearch()
            profilesByPubkey.clear()
            _uiState.value = SearchUiState()
        } else {
            _uiState.value = _uiState.value.copy(isSearching = true)
        }
    }

    fun closeSearch() {
        val previous = activeQuery ?: return
        NMPBridge.closeSearchFeed(previous, SEARCH_FEED_KEY)
        activeQuery = null
    }

    private fun observeSearchResults() {
        NMPBridge.photoFeedsJson
            .onEach { (key, raw) ->
                if (key != SEARCH_FEED_KEY) return@onEach
                if (activeQuery == null) return@onEach
                val posts = runCatching { json.decodeFromString<List<PhotoPost>>(raw) }
                    .getOrNull()
                    ?: return@onEach
                val query = _uiState.value.query
                _uiState.value = _uiState.value.copy(
                    posts = posts,
                    tags = tagsFrom(posts, query),
                    isSearching = false,
                )
            }
            .launchIn(viewModelScope)

        NMPBridge.nostrEvents
            .onEach { raw ->
                if (activeQuery == null) return@onEach
                val profile = NMPBridge.decodeKind0EventJson(raw)
                    ?.let { runCatching { json.decodeFromString<OlasProfile>(it) }.getOrNull() }
                    ?: return@onEach
                profilesByPubkey[profile.pubkey] = profile
                applyProfileResults()
            }
            .launchIn(viewModelScope)
    }

    private fun openSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        if (activeQuery == normalized) return
        closeSearch()
        profilesByPubkey.clear()
        _uiState.value = _uiState.value.copy(
            profiles = emptyList(),
            posts = emptyList(),
            tags = emptyList(),
            isSearching = true,
        )
        activeQuery = normalized
        NMPBridge.openSearchFeed(normalized, SEARCH_FEED_KEY)
    }

    private fun applyProfileResults() {
        val q = _uiState.value.query.lowercase()
        val rows = profilesByPubkey.values
            .filter { profile ->
                listOfNotNull(profile.name, profile.displayName, profile.about, profile.nip05)
                    .any { it.lowercase().contains(q) }
            }
            .sortedBy { it.displayName ?: it.name ?: it.pubkey.take(8) }
        _uiState.value = _uiState.value.copy(profiles = rows, isSearching = false)
    }

    private fun tagsFrom(posts: List<PhotoPost>, query: String): List<String> {
        val q = query.lowercase()
        return posts
            .flatMap { it.hashtags }
            .filter { it.lowercase().contains(q) }
            .distinct()
            .sorted()
    }

    override fun onCleared() {
        closeSearch()
        super.onCleared()
    }
}
