package io.f7z.olas.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.FeedMode
import io.f7z.olas.core.NMPBridge
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
    }

    private fun observeEvents() {
        NMPBridge.nostrEvents
            .onEach { raw ->
                NMPBridge.profileJson(raw)
                    ?.let { runCatching { json.decodeFromString<OlasProfile>(it) }.getOrNull() }
                    ?.takeIf { pubkey == null || it.pubkey == pubkey }
                    ?.let { profile ->
                        _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
                    }

                NMPBridge.photoPostJson(raw, FeedMode.FOLLOWING)
                    ?.let { runCatching { json.decodeFromString<PhotoPost>(it) }.getOrNull() }
                    ?.takeIf { pubkey == null || it.authorPubkey == pubkey }
                    ?.let { post ->
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            posts     = (current.posts + post).distinctBy { it.id }
                                .sortedByDescending { it.createdAt },
                            isLoading = false,
                        )
                    }
            }
            .launchIn(viewModelScope)
    }

    fun toggleFollow() {
        val pk = pubkey ?: return
        if (_uiState.value.isFollowing) NMPBridge.unfollow(pk) else NMPBridge.follow(pk)
    }
}
