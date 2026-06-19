package io.f7z.olas.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.NostrEvent
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.core.PhotoPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

data class ProfileUiState(
    val profile: OlasProfile? = null,
    val posts: List<PhotoPost> = emptyList(),
    val isLoading: Boolean = true,
    val isFollowing: Boolean = false,
)

class ProfileViewModel(val pubkey: String?) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        observeEvents()
        observeFollowState()
    }

    private fun observeEvents() {
        NMPBridge.nostrEvents
            .onEach { raw ->
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: return@onEach
                when (event.kind) {
                    0 -> {
                        // Profile metadata — filter by pubkey if specified
                        if (pubkey != null && event.author != pubkey) return@onEach
                        val profileJson = NMPBridge.decodeKind0EventJson(raw) ?: return@onEach
                        val profile = runCatching { json.decodeFromString<OlasProfile>(profileJson) }.getOrNull() ?: return@onEach
                        _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
                    }
                    20 -> {
                        if (pubkey != null && event.author != pubkey) return@onEach
                        val postJson = NMPBridge.decodeKind20EventJson(raw) ?: return@onEach
                        val post = runCatching { json.decodeFromString<PhotoPost>(postJson) }.getOrNull() ?: return@onEach
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            posts     = (current.posts + post).distinctBy { it.id }
                                .sortedByDescending { it.createdAt },
                            isLoading = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeFollowState() {
        NMPBridge.followedPubkeys
            .onEach { followed ->
                val pk = pubkey ?: return@onEach
                _uiState.value = _uiState.value.copy(isFollowing = followed.contains(pk))
            }
            .launchIn(viewModelScope)
    }

    fun toggleFollow() {
        val pk = pubkey ?: return
        if (_uiState.value.isFollowing) NMPBridge.unfollow(pk) else NMPBridge.follow(pk)
    }
}
