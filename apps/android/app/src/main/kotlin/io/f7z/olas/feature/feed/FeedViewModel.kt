package io.f7z.olas.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.FeedMode
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.PhotoPost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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
        val persistedMode = when (NMPBridge.feedMode()) {
            "following" -> FeedMode.FOLLOWING
            else -> FeedMode.NETWORK
        }
        _uiState.value = _uiState.value.copy(feedMode = persistedMode)
        if (persistedMode == FeedMode.FOLLOWING) NMPBridge.openFollowingFeed() else NMPBridge.openNetworkFeed()
        observeEvents()
        // On cold restart, relay WebSocket connections establish asynchronously.
        // Re-open the subscription at increasing intervals until events arrive.
        viewModelScope.launch {
            for (delayMs in listOf(6_000L, 18_000L, 45_000L, 90_000L)) {
                delay(delayMs)
                if (_uiState.value.isLoading && _uiState.value.posts.isEmpty()) {
                    NMPBridge.openNetworkFeed()
                }
            }
        }
    }

    private fun observeEvents() {
        NMPBridge.nostrEvents
            .onEach { raw ->
                val postJson = NMPBridge.decodeKind20EventJson(raw) ?: return@onEach
                val post = runCatching { json.decodeFromString<PhotoPost>(postJson) }.getOrNull() ?: return@onEach
                val current = _uiState.value
                if (current.posts.isEmpty()) {
                    _uiState.value = current.copy(
                        posts     = (listOf(post) + current.posts).distinctBy { it.id },
                        isLoading = false,
                    )
                } else {
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
            FeedMode.FOLLOWING -> {
                NMPBridge.setFeedMode("following")
                NMPBridge.openFollowingFeed()
            }
            FeedMode.NETWORK   -> {
                NMPBridge.setFeedMode("network")
                NMPBridge.openNetworkFeed()
            }
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

    fun react(post: io.f7z.olas.core.PhotoPost) {
        viewModelScope.launch { NMPBridge.reactTo(post) }
    }

    fun bookmark(post: io.f7z.olas.core.PhotoPost) {
        viewModelScope.launch { NMPBridge.bookmarkEvent(post, true) }
    }

    fun zap(post: io.f7z.olas.core.PhotoPost) {
        viewModelScope.launch { NMPBridge.zapPost(post, 21L) }
    }
}
