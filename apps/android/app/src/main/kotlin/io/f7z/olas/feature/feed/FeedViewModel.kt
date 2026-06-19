package io.f7z.olas.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.FeedMode
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.NostrEvent
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.core.PhotoPostParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

// NMP-GAP(#28): Feed mode state must be owned by a Rust projection, not native ViewModel state.
data class FeedUiState(
    val posts: List<PhotoPost> = emptyList(),
    val pendingPosts: List<PhotoPost> = emptyList(),
    val hasNewPosts: Boolean = false,
    val isLoading: Boolean = false,
    val feedMode: FeedMode = FeedMode.FOLLOWING,
)

class FeedViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState(isLoading = true, feedMode = FeedMode.NETWORK))
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        NMPBridge.openNetworkFeed()
        observeEvents()
    }

    private fun observeEvents() {
        NMPBridge.nostrEvents
            .onEach { raw ->
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: return@onEach
                // NMP-GAP(#9): PhotoPostParser decodes kind:20 events in Kotlin. Must be replaced by a typed Rust snapshot projection.
                val post = PhotoPostParser.parseKind20(event) ?: return@onEach
                val current = _uiState.value
                if (current.posts.isEmpty()) {
                    // First batch — show immediately
                    _uiState.value = current.copy(
                        posts     = (listOf(post) + current.posts).distinctBy { it.id },
                        isLoading = false,
                    )
                } else {
                    // Subsequent posts — queue behind "new posts" pill
                    val pending = (listOf(post) + current.pendingPosts).distinctBy { it.id }
                    _uiState.value = current.copy(
                        pendingPosts = pending,
                        hasNewPosts  = true,
                        isLoading    = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun switchMode(mode: FeedMode) {
        if (_uiState.value.feedMode == mode) return
        _uiState.value = FeedUiState(isLoading = true, feedMode = mode)
        when (mode) {
            FeedMode.FOLLOWING -> NMPBridge.openFollowingFeed()
            FeedMode.NETWORK   -> NMPBridge.openNetworkFeed()
        }
    }

    /** Merge pending posts into visible list (triggered by tapping the pill). */
    fun showNewPosts() {
        val current = _uiState.value
        val merged = (current.pendingPosts + current.posts).distinctBy { it.id }
        _uiState.value = current.copy(
            posts        = merged,
            pendingPosts = emptyList(),
            hasNewPosts  = false,
        )
    }

    fun loadOlderPosts() {
        NMPBridge.loadOlderFeed("photo_feed")
    }
}
