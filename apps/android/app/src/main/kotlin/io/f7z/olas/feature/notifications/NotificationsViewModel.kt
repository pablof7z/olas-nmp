package io.f7z.olas.feature.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.f7z.olas.core.NMPBridge
import io.f7z.olas.core.groupNotificationsJson
import io.f7z.olas.core.NostrEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

data class NotificationsUiState(
    val grouped: List<GroupedNotificationItem> = emptyList(),
    val isLoading: Boolean = true,
)

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // Raw per-notification payload JSON strings accumulated from the event stream.
    private val rawPayloads = mutableListOf<String>()
    // IDs already accumulated (dedup).
    private val seenIds = mutableSetOf<String>()

    init {
        NMPBridge.nostrEvents
            .onEach { raw ->
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: run {
                        if (_uiState.value.isLoading) {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                        return@onEach
                    }
                handleEvent(raw, event)
            }
            .launchIn(viewModelScope)
    }

    private fun handleEvent(raw: String, event: NostrEvent) {
        // Only process notification-relevant event kinds.
        if (event.kind !in setOf(7, 9735, 1, 3)) {
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            return
        }

        // Rust decodes the event and validates it as a notification payload.
        val payload = NMPBridge.notificationJson(raw) ?: run {
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            return
        }

        // Deduplicate by notification ID.
        val id = runCatching { JSONObject(payload).optString("id") }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return
        if (!seenIds.add(id)) return

        rawPayloads.add(payload)
        rebuildGroups()
    }

    private fun rebuildGroups() {
        if (rawPayloads.isEmpty()) return

        // Build JSON array string from accumulated payloads.
        val arrayJson = rawPayloads.joinToString(separator = ",", prefix = "[", postfix = "]")
        val groupedJson = NMPBridge.groupNotificationsJson(arrayJson) ?: return

        val grouped = runCatching { parseGroupedJson(groupedJson) }.getOrElse { emptyList() }
        _uiState.value = _uiState.value.copy(grouped = grouped, isLoading = false)
    }

    private fun parseGroupedJson(json: String): List<GroupedNotificationItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val pubkeysArr = obj.optJSONArray("actorPubkeys") ?: return@mapNotNull null
            val pubkeys = (0 until pubkeysArr.length()).map { pubkeysArr.getString(it) }
            GroupedNotificationItem(
                groupId     = obj.optString("groupId"),
                kind        = obj.optString("kind"),
                targetPostId = obj.optString("targetPostId").takeIf { it.isNotEmpty() },
                actorPubkeys = pubkeys,
                count       = obj.optInt("count", 1),
                latestTs    = obj.optLong("latestTs"),
                zapSats     = obj.optLong("zapSats").takeIf { obj.has("zapSats") },
            )
        }
    }
}
