package io.f7z.olas.feature.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.ApplyFollowPacksResult
import io.f7z.olas.core.FollowPacksSnapshot
import io.f7z.olas.core.InviteStore
import io.f7z.olas.core.resolveInviteJson
import io.f7z.olas.core.NMPBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// Step ordering is driven by NMPBridge.onboardingStepsJson(); local enum mirrors canonical Rust order.
enum class OnboardingStep {
    WELCOME, CREATE_ACCOUNT, FOLLOW_PACKS, COMPLETE
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val isLoading: Boolean = false,
    val error: String? = null,
    val displayName: String = "",
    val username: String = "",
    // Follow packs: the entire projection lives in Rust; native re-reads this
    // snapshot on every kernel update frame (event-driven; no polling).
    val followPacks: FollowPacksSnapshot? = null,
    val selectedPackIds: Set<String> = emptySet(),
    // P2-A: inbound invite — populated when launched via an invite link.
    val inviterPubkey: String? = null,
    val inviterDisplayHint: String? = null,
)

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // Consume Rust-defined step ordering (no-op if null — Rust not yet wired).
    private val onboardingStepsJson: String? = NMPBridge.onboardingStepsJson()

    // P0-A: event observer job for kind:30000 pack events
    private var packDiscoveryStarted = false

    fun setDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun setUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun createAccount() {
        val state = _uiState.value
        if (state.displayName.isBlank()) return
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                NMPBridge.createAccount(
                    name     = state.displayName.trim(),
                    username = state.username.trim().ifEmpty { state.displayName.trim().lowercase() },
                )
                markOnboardingComplete()
                // Flag that this is a brand-new account so the first-post coachmark is eligible.
                // Sign-in paths (nsec/bunker) do NOT set this flag, preventing the coachmark
                // from appearing for existing users who already have posts.
                markNewAccount()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    step      = OnboardingStep.FOLLOW_PACKS,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error     = "Couldn't create account — please try again.",
                )
            }
        }
    }

    // Secret format validation is performed by Rust (NMPBridge.signInNsec rejects invalid keys).
    fun signInNsec(nsec: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                NMPBridge.signInNsec(nsec)
                markOnboardingComplete()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    step      = OnboardingStep.COMPLETE,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error     = "That doesn't look right — a recovery key starts with nsec1.",
                )
            }
        }
    }

    fun advanceTo(step: OnboardingStep) {
        _uiState.value = _uiState.value.copy(step = step)
    }

    /**
     * P2-A: consume the pending invite token from InviteStore (written by
     * MainActivity when the app was opened via an invite link). Called once
     * from WelcomeScreen.LaunchedEffect. Clears the store so it is not
     * re-consumed on the next launch.
     */
    fun consumePendingInvite() {
        val token = InviteStore.pendingToken ?: return
        InviteStore.pendingToken = null
        val resultJson = NMPBridge.resolveInviteJson(token) ?: return
        runCatching {
            val obj = org.json.JSONObject(resultJson)
            val pk   = obj.optString("inviter_pubkey").takeIf { it.isNotEmpty() } ?: return
            val hint = obj.optString("display_hint").takeIf { it.isNotEmpty() }
            _uiState.value = _uiState.value.copy(
                inviterPubkey       = pk,
                inviterDisplayHint  = hint,
            )
        }
    }

    fun togglePackSelection(packId: String) {
        val current = _uiState.value.selectedPackIds
        _uiState.value = _uiState.value.copy(
            selectedPackIds = if (packId in current) current - packId else current + packId
        )
    }

    // MARK: - Follow-pack discovery (event-driven snapshot refresh)

    private var didSeedDefaults = false
    private var lastFollowPacksJson = ""

    /**
     * Open discovery and re-read the snapshot on every kernel update frame.
     * Event-driven only — the collector reacts to [NMPBridge.events] push frames;
     * there is no Timer or poll loop.
     */
    fun startPackDiscovery() {
        if (packDiscoveryStarted) return
        packDiscoveryStarted = true
        NMPBridge.openFollowPackDiscovery()
        reloadFollowPacks()
        viewModelScope.launch {
            NMPBridge.events.collect { reloadFollowPacks() }
        }
    }

    private fun reloadFollowPacks() {
        val raw = NMPBridge.followPacksSnapshotJson() ?: return
        if (raw == lastFollowPacksJson) return
        lastFollowPacksJson = raw
        val snapshot = runCatching {
            json.decodeFromString(FollowPacksSnapshot.serializer(), raw)
        }.getOrNull() ?: return

        var selected = _uiState.value.selectedPackIds
        // Pre-select default packs once, the first time they become available.
        if (!didSeedDefaults && snapshot.state == "ready") {
            val defaults = snapshot.packs.filter { it.defaultSelected }.map { it.id }
            if (defaults.isNotEmpty()) {
                selected = selected + defaults
                didSeedDefaults = true
            }
        }
        _uiState.value = _uiState.value.copy(followPacks = snapshot, selectedPackIds = selected)
    }

    /** Close the discovery interest (call from onDisappear / onCleared). */
    fun stopPackDiscovery() {
        NMPBridge.closeFollowPackDiscovery()
    }

    /**
     * Apply the selection: forward the opaque ids to Rust (which expands p-tags,
     * unions, dedups, excludes self and dispatches one follow_many), then advance.
     * The returned feed_default is informational — the kernel already flips the
     * feed. P2-A: an inbound invite pubkey is followed separately.
     */
    fun applySelectedPacks() {
        val state = _uiState.value
        val ids = state.selectedPackIds.toList()

        viewModelScope.launch {
            if (ids.isNotEmpty()) {
                val idsJson = "[${ids.joinToString(",") { "\"$it\"" }}]"
                val resultJson = NMPBridge.applySelectedFollowPacks(idsJson)
                if (resultJson != null) {
                    runCatching {
                        json.decodeFromString(ApplyFollowPacksResult.serializer(), resultJson)
                    }.getOrNull()
                }
            }
            // P2-A: follow the inviter (if any) via a plain nmp.follow.
            state.inviterPubkey?.takeIf { it.isNotEmpty() }?.let { NMPBridge.follow(it) }
            markOnboardingComplete()
            advanceTo(OnboardingStep.COMPLETE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPackDiscovery()
    }

    private fun markOnboardingComplete() {
        getApplication<Application>()
            .getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_complete", true)
            .apply()
    }

    private fun markNewAccount() {
        getApplication<Application>()
            .getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_new_account", true)
            .apply()
    }
}
