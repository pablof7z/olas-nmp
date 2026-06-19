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

/** Parsed metadata for a single image within a post. */
@Serializable
data class ImageMeta(
    val url: String,
    val sha256: String? = null,
    val mime: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val alt: String? = null,
)

/** A fully-parsed kind-20 photo post ready for rendering. */
@Serializable
data class PhotoPost(
    val id: String,
    val authorPubkey: String,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val images: List<ImageMeta>,
    val caption: String,
    val hashtags: List<String>,
    val reactionCount: Int,
    val commentCount: Int,
    val zapTotal: Long,
    val createdAt: Long,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
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

@Serializable
data class OlasNotificationPayload(
    val id: String,
    val kind: String,
    val actorPubkey: String,
    val postId: String? = null,
    val createdAt: Long,
    val zapSats: Long? = null,
)

@Serializable
data class DefaultRelay(
    val id: String,
    val name: String,
    val iconHost: String,
    val url: String,
    val role: String,
    val connected: Boolean,
)

/** Which feed source the user is currently viewing. */
enum class FeedMode { FOLLOWING, NETWORK }

const val WOT_NETWORK_FEED_LABEL = "Filtered by your trust settings"
const val WOT_NOTIFICATIONS_NOTE = "Filtered by your trust settings"
const val WOT_SETTINGS_NOTE =
    "Network uses your local trust graph. Close is stricter; Open still hides accounts you mute."
