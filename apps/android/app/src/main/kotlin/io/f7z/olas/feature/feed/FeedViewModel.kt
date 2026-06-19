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
        // Restore feed mode from Rust (persisted across restarts).
        val persistedMode = when (NMPBridge.feedMode()) {
            "following" -> FeedMode.FOLLOWING
            else -> FeedMode.NETWORK
        }
        _uiState.value = _uiState.value.copy(feedMode = persistedMode)
        if (persistedMode == FeedMode.FOLLOWING) NMPBridge.openFollowingFeed() else NMPBridge.openNetworkFeed()
        observeEvents()
    }

    private fun observeEvents() {
        NMPBridge.nostrEvents
            .onEach { raw ->
                val postJson = NMPBridge.photoPostJson(raw, _uiState.value.feedMode) ?: return@onEach
                val post = runCatching { json.decodeFromString<PhotoPost>(postJson) }.getOrNull() ?: return@onEach
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

    fun react(post: PhotoPost) {
        if (post.isLiked) return
        NMPBridge.reactTo(post)
    }

    fun bookmark(post: PhotoPost) {
        NMPBridge.bookmarkEvent(post, add = !post.isBookmarked)
    }

    fun zap(post: PhotoPost, amountSats: Long = 21) {
        NMPBridge.zapPost(post, amountSats)
    }
}
