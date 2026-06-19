package io.f7z.olas.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.nmp.registry.NostrProfileHost
import org.nmp.registry.ProfileWire

/**
 * App-level implementation of [NostrProfileHost].
 *
 * Two profile sources:
 *  1. [NMPBridge.claimedProfilesJson] — snapshot projection `claimed_profiles`
 *     decoded by Rust; the primary and most reliable source (same as iOS).
 *  2. [NMPBridge.nostrEvents] — raw kind:0 events from the relay stream,
 *     used as a fallback / supplement.
 */
object OlasProfileHost : NostrProfileHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val profiles = MutableStateFlow<Map<String, ProfileWire>>(emptyMap())
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Primary: claimed_profiles snapshot projection (Rust-formatted).
        NMPBridge.claimedProfilesJson
            .onEach { raw -> parseClaimedProfiles(raw) }
            .launchIn(scope)

        // Fallback: raw kind:0 events from the relay event observer.
        NMPBridge.nostrEvents
            .onEach { raw ->
                val event = runCatching { json.decodeFromString<NostrEvent>(raw) }.getOrNull()
                    ?: return@onEach
                if (event.kind != 0) return@onEach
                val wire = parseProfileFromEvent(event) ?: return@onEach
                profiles.update { it + (wire.pubkey to wire) }
            }
            .launchIn(scope)
    }

    @Composable
    override fun profileForPubkey(pubkey: String): ProfileWire? {
        val map by profiles.collectAsState()
        return map[pubkey]
    }

    override fun claimProfile(pubkey: String, consumerId: String) =
        NMPBridge.claimProfile(pubkey, consumerId)
    override fun releaseProfile(pubkey: String, consumerId: String) =
        NMPBridge.releaseProfile(pubkey, consumerId)

    // ── Parsers ───────────────────────────────────────────────────────────────

    private fun parseClaimedProfiles(raw: String) {
        val arr = runCatching { json.decodeFromString<JsonArray>(raw) }.getOrNull() ?: return
        val batch = buildMap {
            for (elem in arr) {
                val obj = elem as? JsonObject ?: continue
                fun str(k: String) = obj[k]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val pubkey = str("pubkey") ?: continue
                val npubShort = str("npub_short") ?: run {
                    if (pubkey.length >= 12) "${pubkey.take(8)}…${pubkey.takeLast(4)}" else pubkey
                }
                put(pubkey, ProfileWire(
                    pubkey      = pubkey,
                    displayName = str("display_name") ?: str("name"),
                    about       = str("about"),
                    pictureUrl  = str("picture_url"),
                    nip05       = str("nip05"),
                    npub        = str("npub") ?: pubkey,
                    npubShort   = npubShort,
                ))
            }
        }
        if (batch.isNotEmpty()) profiles.update { it + batch }
    }

    private fun parseProfileFromEvent(event: NostrEvent): ProfileWire? {
        val content = runCatching {
            json.decodeFromString<JsonObject>(event.content)
        }.getOrNull() ?: return null
        fun str(k: String) = content[k]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val pubkey = event.author
        val npubShort = if (pubkey.length >= 12)
            "${pubkey.take(8)}…${pubkey.takeLast(4)}" else pubkey
        return ProfileWire(
            pubkey      = pubkey,
            displayName = str("display_name") ?: str("name"),
            about       = str("about"),
            pictureUrl  = str("picture"),
            nip05       = str("nip05"),
            npub        = pubkey,
            npubShort   = npubShort,
        )
    }
}
