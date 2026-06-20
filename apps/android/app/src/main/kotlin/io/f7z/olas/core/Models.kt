package io.f7z.olas.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Raw Nostr event as received from the kernel event observer JSON.
 *
 * NMP KernelEvent JSON shape uses "author" for the pubkey field. */
@Serializable
data class NostrEvent(
    val id: String,
    @SerialName("author") val author: String,
    val kind: Int,
    val content: String,
    val tags: List<List<String>>,
    val created_at: Long,
)

/** Mirrors nmp_nip92_types::MediaDimensions JSON. */
@Serializable
data class ImageDimensions(val width: Int, val height: Int)

/** Mirrors nmp_nip92_types::MediaMeta JSON (aliased as ImageMeta in nmp_nip68). */
@Serializable
data class ImageMeta(
    val url: String,
    val sha256: String? = null,
    val mime: String? = null,
    val dimensions: ImageDimensions? = null,
    val blurhash: String? = null,
    val alt: String? = null,
)

/** Mirrors nmp_nip68::PictureEventRecord JSON. */
@Serializable
data class PhotoPost(
    @SerialName("event_id") val id: String,
    @SerialName("author") val authorPubkey: String,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val images: List<ImageMeta>,
    @SerialName("content") val content: String,
    val hashtags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    val repostedBy: PhotoRepostAttribution? = null,
    // Client-only counters — not in Rust JSON output.
    val reactionCount: Int = 0,
    val commentCount: Int = 0,
    val zapTotal: Long = 0L,
) {
    val caption: String get() = content
}

@Serializable
data class PhotoRepostAttribution(
    val authorPubkey: String,
    val repostEventId: String,
    val repostCreatedAt: Long,
)

/** A Nostr profile (kind-0). */
@Serializable
data class OlasProfile(
    val pubkey: String,
    val name: String? = null,
    val displayName: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val banner: String? = null,
    val nip05: String? = null,
    val lud16: String? = null,
)

/** A curated starter follow pack (NIP-51 kind:30000). */
@Serializable
data class FollowPack(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val accentColor: String,
    val count: Int,
)

/** Which feed source the user is currently viewing. */
enum class FeedMode { FOLLOWING, NETWORK }

/** A relay entry from the default relay list. */
@Serializable
data class DefaultRelay(
    val id: String,
    val name: String,
    val iconHost: String,
    val url: String,
    val role: String,
    val connected: Boolean,
)
