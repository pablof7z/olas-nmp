package io.f7z.olas.feature.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.NostrEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

data class NotificationsUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = true,
)

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        NMPBridge.nostrEvents
            .onEach { raw ->
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: run {
                        // Not a valid NostrEvent frame — mark loading done on first frame.
                        if (_uiState.value.isLoading) {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                        return@onEach
                    }
                handleEvent(event)
            }
            .launchIn(viewModelScope)
    }

    private fun handleEvent(event: NostrEvent) {
        val item: NotificationItem? = when (event.kind) {
            7 -> {
                // kind 7 — reaction to one of our posts
                NotificationItem(
                    id           = event.id,
                    type         = NotificationType.REACTION,
                    actorName    = event.author.take(8),
                    actorAvatar  = null,
                    body         = "reacted to your photo.",
                    thumbnailUrl = null,
                    createdAt    = event.created_at,
                )
            }
            9735 -> {
                // kind 9735 — zap receipt
                val bolt11 = event.tags.firstOrNull { it.firstOrNull() == "bolt11" }?.getOrNull(1) ?: ""
                val msats = if (bolt11.isNotEmpty()) NMPBridge.bolt11AmountMsats(bolt11) else 0L
                val sats = msats / 1000
                NotificationItem(
                    id           = event.id,
                    type         = NotificationType.ZAP,
                    actorName    = event.author.take(8),
                    actorAvatar  = null,
                    body         = "zapped you $sats sats.",
                    thumbnailUrl = null,
                    createdAt    = event.created_at,
                )
            }
            1 -> {
                // kind 1 — text note mentioning us (comment / mention)
                NotificationItem(
                    id           = event.id,
                    type         = NotificationType.MENTION,
                    actorName    = event.author.take(8),
                    actorAvatar  = null,
                    body         = "mentioned you.",
                    thumbnailUrl = null,
                    createdAt    = event.created_at,
                )
            }
            3 -> {
                // kind 3 — contact list update (new follow)
                NotificationItem(
                    id           = event.id,
                    type         = NotificationType.FOLLOW,
                    actorName    = event.author.take(8),
                    actorAvatar  = null,
                    body         = "started following you.",
                    thumbnailUrl = null,
                    createdAt    = event.created_at,
                )
            }
            else -> null
        }

        val current = _uiState.value
        if (item != null) {
            _uiState.value = current.copy(
                notifications = (listOf(item) + current.notifications).distinctBy { it.id },
                isLoading     = false,
            )
        } else if (current.isLoading) {
            // Any event frame clears the initial loading state.
            _uiState.value = current.copy(isLoading = false)
        }
    }
}
