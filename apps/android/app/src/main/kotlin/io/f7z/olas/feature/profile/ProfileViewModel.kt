package io.f7z.olas.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.NostrEvent
import io.f7z.olas.core.OlasProfile
import io.f7z.olas.core.PhotoPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONArray

data class ProfileUiState(
    val profile: OlasProfile? = null,
    val posts: List<PhotoPost> = emptyList(),
    val isLoading: Boolean = true,
    val isFollowing: Boolean = false,
    /** Raw Rust JSON for the social-proof row; null until queried or when graph empty. */
    val socialProofJson: String? = null,
    // Live "Following" count for the active account's own profile, read from the
    // local kind:3 (read-your-writes). Only meaningful for the own profile.
    val followingCount: Int = 0,
)

class ProfileViewModel(private val requestedPubkey: String?) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // Resolved target pubkey. For own profile: pre-populate from the already-resolved
    // singleton if available so replay-buffer events aren't filtered while null.
    @Volatile
    private var targetPubkey: String? = requestedPubkey ?: NMPBridge.activeAccountPubkey

    // Own profile when no specific pubkey was requested (or it matches the active account).
    private val isOwnProfile: Boolean
        get() = requestedPubkey == null || requestedPubkey == NMPBridge.activeAccountPubkey

    init {
        // Start listening immediately — non-blocking flow subscriptions.
        listenForProfile()

        // Read the live Following count from the local kind:3. The contact list
        // may not be ingested the instant the screen opens (e.g. right after
        // onboarding applies a follow pack), so poll briefly until it lands.
        if (isOwnProfile) {
            viewModelScope.launch {
                repeat(20) {
                    val count = NMPBridge.activeFollowingCount()
                    if (count != _uiState.value.followingCount) {
                        _uiState.value = _uiState.value.copy(followingCount = count)
                    }
                    if (count > 0) return@launch
                    delay(250L)
                }
            }
        }

        // Claim profile on IO thread so we never block the main thread waiting on
        // the Rust actor channel (which may be busy delivering feed events).
        viewModelScope.launch(Dispatchers.IO) {
            val pk = if (requestedPubkey != null) {
                requestedPubkey
            } else {
                NMPBridge.activeAccountPubkey
                    ?: NMPBridge.activeAccountPubkeyFlow.filterNotNull().first()
            }
            targetPubkey = pk
            NMPBridge.claimProfile(pk, "profile_screen")
            NMPBridge.openAuthorPhotoFeed(pk)
            // Load social proof for other profiles (own profile has no social proof).
            if (requestedPubkey != null) {
                val activePk = NMPBridge.activeAccountPubkey
                    ?: NMPBridge.activeAccountPubkeyFlow.filterNotNull().first()
                val proofJson = NMPBridge.socialProofJson(activePk, pk)
                if (proofJson != null) {
                    _uiState.value = _uiState.value.copy(socialProofJson = proofJson)
                }
            }
        }

        // After 10s with no profile, show a minimal stub using the pubkey.
        // If we still don't know the pubkey, keep spinning rather than show "unknown".
        viewModelScope.launch {
            delay(10_000L)
            if (_uiState.value.profile == null) {
                val pk = targetPubkey ?: NMPBridge.activeAccountPubkey ?: return@launch
                _uiState.value = _uiState.value.copy(
                    profile = OlasProfile(pubkey = pk, name = pk.take(8)),
                    isLoading = false,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        targetPubkey?.let {
            NMPBridge.releaseProfile(it, "profile_screen")
            NMPBridge.closeAuthorPhotoFeed(it)
        }
    }

    private fun listenForProfile() {
        // Primary: Rust-decoded claimed profiles snapshot
        NMPBridge.claimedProfilesJson
            .onEach { raw -> parseClaimedProfiles(raw) }
            .launchIn(viewModelScope)

        // Supplement: raw kind:0 and kind:20 events
        NMPBridge.nostrEvents
            .onEach { raw ->
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: return@onEach
                when (event.kind) {
                    3 -> {
                        // Active account's contact list changed — refresh the count.
                        if (isOwnProfile && event.author == NMPBridge.activeAccountPubkey) {
                            val count = NMPBridge.activeFollowingCount()
                            _uiState.value = _uiState.value.copy(followingCount = count)
                        }
                    }
                    0 -> {
                        if (targetPubkey != null && event.author != targetPubkey) return@onEach
                        val profileJson = NMPBridge.decodeKind0EventJson(raw) ?: return@onEach
                        val profile = runCatching { json.decodeFromString<OlasProfile>(profileJson) }.getOrNull() ?: return@onEach
                        _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
                    }
                    20 -> {
                        // Skip posts from unknown author — avoids polluting the grid with
                        // replayed network-feed events before the own pubkey is resolved.
                        if (event.author != targetPubkey) return@onEach
                        val postJson = NMPBridge.decodeKind20EventJson(raw) ?: return@onEach
                        val post = runCatching { json.decodeFromString<PhotoPost>(postJson) }.getOrNull() ?: return@onEach
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            posts = (current.posts + post).distinctBy { it.id }
                                .sortedByDescending { it.createdAt },
                            isLoading = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun parseClaimedProfiles(raw: String) {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val pk = obj.optString("pubkey").takeIf { it.isNotEmpty() } ?: continue
            if (targetPubkey != null && pk != targetPubkey) continue
            fun str(k: String) = obj.optString(k).takeIf { it.isNotEmpty() }
            val profile = OlasProfile(
                pubkey      = pk,
                name        = str("name"),
                displayName = str("display_name"),
                about       = str("about"),
                picture     = str("picture_url"),
                banner      = null,
                nip05       = str("nip05"),
                lud16       = null,
            )
            _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
            return
        }
    }

    fun toggleFollow() {
        val following = !_uiState.value.isFollowing
        _uiState.value = _uiState.value.copy(isFollowing = following)
        val pk = targetPubkey ?: return
        if (following) NMPBridge.follow(pk) else NMPBridge.unfollow(pk)
    }
}
