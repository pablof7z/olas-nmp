package org.nmp.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire type for a Nostr user profile, decoded from the `nmp-profile`
 * projection emitted by the kernel.
 *
 * `npub` and `npubShort` are always Rust-formatted — never reformat
 * them in Kotlin.
 */
@Serializable
data class ProfileWire(
    @SerialName("pubkey") val pubkey: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("about") val about: String? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
    @SerialName("nip05") val nip05: String? = null,
    /** Full bech32 `npub1…` string. Use for copy / share. */
    @SerialName("npub") val npub: String,
    /** Rust-truncated npub (e.g. `npub1abcd…wxyz`). Display only. */
    @SerialName("npub_short") val npubShort: String,
) {
    /** Stable display label: `displayName` if set, else `npubShort`. */
    val display: String get() = displayName?.takeIf { it.isNotEmpty() } ?: npubShort

    /** Avatar URL string; `null` when no picture is set or empty. */
    val avatarUrl: String? get() = pictureUrl?.takeIf { it.isNotEmpty() }
}
