package io.f7z.olas.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.NostrEvent
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.core.PhotoPost
import io.f7z.olas.core.PhotoPostParser
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
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: return@onEach
                when (event.kind) {
                    0 -> {
                        // Profile metadata — filter by pubkey if specified
                        if (pubkey != null && event.author != pubkey) return@onEach
                        val profile = parseProfile(event) ?: return@onEach
                        _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
                    }
                    20 -> {
                        if (pubkey != null && event.author != pubkey) return@onEach
                        val post = PhotoPostParser.parseKind20(event) ?: return@onEach
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

    private fun parseProfile(event: NostrEvent): OlasProfile? {
        val content = runCatching {
            json.decodeFromString<kotlinx.serialization.json.JsonObject>(event.content)
        }.getOrNull() ?: return null
        return OlasProfile(
            pubkey      = event.author,
            name        = content["name"]?.toString()?.trim('"'),
            displayName = content["display_name"]?.toString()?.trim('"'),
            about       = content["about"]?.toString()?.trim('"'),
            picture     = content["picture"]?.toString()?.trim('"'),
            banner      = content["banner"]?.toString()?.trim('"'),
            nip05       = content["nip05"]?.toString()?.trim('"'),
            lud16       = content["lud16"]?.toString()?.trim('"'),
        )
    }

    fun toggleFollow() {
        val following = !_uiState.value.isFollowing
        _uiState.value = _uiState.value.copy(isFollowing = following)
        val pk = pubkey ?: return
        if (following) NMPBridge.follow(pk) else NMPBridge.unfollow(pk)
    }
}
