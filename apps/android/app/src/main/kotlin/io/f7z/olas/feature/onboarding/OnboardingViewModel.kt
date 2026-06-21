package io.f7z.olas.feature.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.FollowPackApplyResult
import io.f7z.olas.core.FollowPackDescriptor
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
    // P0-A: packs discovered from kind:30000 events via the event observer
    val discoveredPacks: List<FollowPackDescriptor> = emptyList(),
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

    // MARK: - P0-A: follow-pack discovery

    /** Open kind:30000 interest and collect arriving pack events. */
    fun startPackDiscovery() {
        if (packDiscoveryStarted) return
        packDiscoveryStarted = true
        NMPBridge.openFollowPackDiscovery()
        viewModelScope.launch {
            NMPBridge.nostrEvents.collect { rawJson ->
                val descriptorJson = NMPBridge.decodeFollowPackEventJson(rawJson) ?: return@collect
                runCatching {
                    json.decodeFromString(FollowPackDescriptor.serializer(), descriptorJson)
                }.getOrNull()?.let { descriptor ->
                    val current = _uiState.value.discoveredPacks
                    val idx = current.indexOfFirst { it.id == descriptor.id }
                    val updated = if (idx >= 0) {
                        current.toMutableList().also { it[idx] = descriptor }
                    } else {
                        current + descriptor
                    }
                    _uiState.value = _uiState.value.copy(discoveredPacks = updated)
                }
            }
        }
    }

    /** Close the discovery interest (call from onDisappear / onCleared). */
    fun stopPackDiscovery() {
        NMPBridge.closeFollowPackDiscovery()
    }

    /**
     * Apply selected packs: collect all pubkeys from selected descriptors plus
     * the inviter pubkey (P2-A), pass to Rust for dedup + self-exclusion, then
     * advance. The entire set goes through a single olas_apply_follow_pack_pubkeys
     * call — one kind:3 event, race-free (same P0-A path).
     */
    fun applySelectedPacks() {
        val state = _uiState.value
        val selectedIds = state.selectedPackIds

        // Union of pack pubkeys + inviter pubkey (dedup happens in Rust).
        val allPubkeys = state.discoveredPacks
            .filter { it.id in selectedIds }
            .flatMap { it.pubkeys }
            .toMutableList()
            .also { list ->
                state.inviterPubkey?.takeIf { it.isNotEmpty() }?.let { list.add(it) }
            }

        if (allPubkeys.isEmpty()) {
            viewModelScope.launch {
                markOnboardingComplete()
                advanceTo(OnboardingStep.COMPLETE)
            }
            return
        }
        viewModelScope.launch {
            val pubkeysJson = "[${allPubkeys.joinToString(",") { "\"$it\"" }}]"
            val activePubkey = NMPBridge.activeAccountPubkey ?: ""
            val resultJson = NMPBridge.applyFollowPackPubkeys(pubkeysJson, activePubkey)
            if (resultJson != null) {
                runCatching {
                    json.decodeFromString(FollowPackApplyResult.serializer(), resultJson)
                }.getOrNull()?.let { result ->
                    if (result.feedDefault == "following") {
                        NMPBridge.setFeedMode("following")
                    }
                }
            }
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
