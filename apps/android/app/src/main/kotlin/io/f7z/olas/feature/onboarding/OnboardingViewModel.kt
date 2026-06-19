package io.f7z.olas.feature.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// NMP-GAP(#32): Onboarding step routing and completion state must be driven by a Rust state machine.
enum class OnboardingStep {
    WELCOME, CREATE_ACCOUNT, FOLLOW_PACKS, MEDIA_SERVER, COMPLETE
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val isLoading: Boolean = false,
    val error: String? = null,
    val displayName: String = "",
    val username: String = "",
)

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

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

    // NMP-GAP(#27): Secret format validation must be performed by Rust, not Kotlin.
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

    private fun markOnboardingComplete() {
        getApplication<Application>()
            .getSharedPreferences("olas_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_complete", true)
            .apply()
    }
}
