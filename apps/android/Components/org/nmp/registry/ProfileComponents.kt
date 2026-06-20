package org.nmp.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

data class ProfileWire(
    val pubkey: String,
    val displayName: String? = null,
    val about: String? = null,
    val pictureUrl: String? = null,
    val nip05: String? = null,
    val npub: String = pubkey,
    val npubShort: String = pubkey.take(12),
) {
    val display: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: npubShort.takeIf { it.isNotBlank() }
            ?: pubkey.take(8)
}

interface NostrProfileHost {
    @Composable
    fun profileForPubkey(pubkey: String): ProfileWire?
    fun claimProfile(pubkey: String, consumerId: String)
    fun releaseProfile(pubkey: String, consumerId: String)
}

val LocalNostrProfileHost = compositionLocalOf<NostrProfileHost?> { null }
