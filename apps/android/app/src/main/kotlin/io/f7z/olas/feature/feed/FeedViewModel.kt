package io.f7z.olas.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.FeedMode
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.PhotoPost
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
    private companion object {
        const val FOLLOWING_FEED_KEY = "olas.following_feed"
        const val NETWORK_FEED_KEY = "olas.network_feed"
    }

    private val _uiState = MutableStateFlow(FeedUiState(isLoading = true, feedMode = FeedMode.NETWORK))
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        val persistedMode = when (NMPBridge.feedMode()) {
            "following" -> FeedMode.FOLLOWING
            else -> FeedMode.NETWORK
        }
        _uiState.value = _uiState.value.copy(feedMode = persistedMode)
        observePhotoFeeds()
        openFeed(persistedMode)
    }

    private fun observePhotoFeeds() {
        NMPBridge.photoFeedsJson
            .onEach { (key, raw) ->
                val current = _uiState.value
                if (key != feedKey(current.feedMode)) return@onEach
                val posts = runCatching { json.decodeFromString<List<PhotoPost>>(raw) }
                    .getOrNull()
                    ?: return@onEach
                _uiState.value = current.copy(
                    posts = posts,
                    pendingPosts = emptyList(),
                    hasNewPosts = false,
                    isLoading = false,
                )
            }
            .launchIn(viewModelScope)
    }

    fun switchMode(mode: FeedMode) {
        if (_uiState.value.feedMode == mode) return
        _uiState.value = FeedUiState(isLoading = true, feedMode = mode)
        openFeed(mode)
    }

    private fun openFeed(mode: FeedMode) {
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
        NMPBridge.loadOlderFeed(feedKey(_uiState.value.feedMode))
    }

    private fun feedKey(mode: FeedMode): String =
        if (mode == FeedMode.FOLLOWING) FOLLOWING_FEED_KEY else NETWORK_FEED_KEY

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
