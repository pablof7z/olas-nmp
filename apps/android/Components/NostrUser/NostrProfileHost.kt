package org.nmp.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Host bridge for profile projections owned by the NMP kernel.
 *
 * Registry components call this bridge with stable Nostr references. The app
 * supplies the platform adapter; the component owns when to claim, release,
 * and re-read the current projection.
 */
interface NostrProfileHost {
    @Composable
    fun profileForPubkey(pubkey: String): ProfileWire?
    fun claimProfile(pubkey: String, consumerId: String)
    fun releaseProfile(pubkey: String, consumerId: String)
}

val LocalNostrProfileHost = staticCompositionLocalOf<NostrProfileHost?> { null }
