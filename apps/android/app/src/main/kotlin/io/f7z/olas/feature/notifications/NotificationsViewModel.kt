package io.f7z.olas.feature.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.OlasNotificationPayload
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
                val payload = NMPBridge.notificationJson(raw)
                    ?.let { runCatching { json.decodeFromString<OlasNotificationPayload>(it) }.getOrNull() }
                    ?: return@onEach
                handleEvent(payload)
            }
            .launchIn(viewModelScope)
    }

    private fun handleEvent(payload: OlasNotificationPayload) {
        val item: NotificationItem? = when (payload.kind) {
            "reaction" -> {
                NotificationItem(
                    id           = payload.id,
                    type         = NotificationType.REACTION,
                    actorName    = payload.actorPubkey.take(8),
                    actorAvatar  = null,
                    body         = "reacted to your photo.",
                    thumbnailUrl = null,
                    createdAt    = payload.createdAt,
                )
            }
            "zap" -> {
                val amount = payload.zapSats
                NotificationItem(
                    id           = payload.id,
                    type         = NotificationType.ZAP,
                    actorName    = payload.actorPubkey.take(8),
                    actorAvatar  = null,
                    body         = amount?.let { "zapped you $it sats." } ?: "zapped you.",
                    thumbnailUrl = null,
                    createdAt    = payload.createdAt,
                )
            }
            "comment" -> {
                NotificationItem(
                    id           = payload.id,
                    type         = NotificationType.MENTION,
                    actorName    = payload.actorPubkey.take(8),
                    actorAvatar  = null,
                    body         = "mentioned you.",
                    thumbnailUrl = null,
                    createdAt    = payload.createdAt,
                )
            }
            "follow" -> {
                NotificationItem(
                    id           = payload.id,
                    type         = NotificationType.FOLLOW,
                    actorName    = payload.actorPubkey.take(8),
                    actorAvatar  = null,
                    body         = "started following you.",
                    thumbnailUrl = null,
                    createdAt    = payload.createdAt,
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
