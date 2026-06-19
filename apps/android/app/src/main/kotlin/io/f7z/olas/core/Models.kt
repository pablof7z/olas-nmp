package io.f7z.olas.core

import kotlinx.serialization.Serializable

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
