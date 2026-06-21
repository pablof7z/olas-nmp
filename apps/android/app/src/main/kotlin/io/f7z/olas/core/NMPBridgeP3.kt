package io.f7z.olas.core

/**
 * Wave-6 (P3-B/C/D) and P0-E/F extensions on [NMPBridge].
 *
 * Extracted to keep NMPBridge.kt under the 500-LOC hard ceiling
 * (AGENTS.md §File Size). Public API is identical to before the split:
 * callers reference `NMPBridge.groupNotificationsJson(...)` etc. unchanged.
 *
 * Each extension delegates to an `internal fun *Impl` in [NMPBridge] that
 * calls the private JNI `external fun`. This avoids changing the visibility
 * of `external fun` declarations (which could affect JVM bytecode naming and
 * break JNI static-name resolution on the Rust side).
 */

// --- P3-B: grouped notifications -----------------------------------------------

/** Group a JSON array of individual notification payloads into clustered rows. */
fun NMPBridge.groupNotificationsJson(notificationsJson: String): String? =
    groupNotificationsJsonImpl(notificationsJson)

// --- P3-C: caption tag parsing -------------------------------------------------

/** Parse `nostr:npub1…` mentions and `#hashtag` tokens from a caption. */
fun NMPBridge.parseCaptionTagsJson(caption: String): String? =
    parseCaptionTagsJsonImpl(caption)

/**
 * Extended picture-post publish that injects `p` and `t` tags extracted
 * from the caption. [extraTagsJson] is a JSON array of tag arrays, e.g.
 * `[["p","<hex>"],["t","bitcoin"]]`. Pass null for the same result as
 * [picturePostPublishJson].
 */
fun NMPBridge.picturePostPublishTaggedJson(
    uploadedImagesJson: String,
    caption: String?,
    geohash: String?,
    extraTagsJson: String?,
): String? = picturePostPublishTaggedJsonImpl(uploadedImagesJson, caption, geohash, extraTagsJson)

// --- P3-D: recovery key export ------------------------------------------------

/**
 * Return the active local account's Recovery Key (bech32 nsec format).
 * Returns null when no local account is signed in.
 * DO NOT log the return value — it is the raw secret key.
 */
fun NMPBridge.activeAccountRecoveryKey(): String? = activeAccountRecoveryKeyImpl()

// --- P0-E: real social proof --------------------------------------------------

/**
 * Query social proof for [targetPubkey] from [activePubkey]'s follow graph.
 * Returns: `{"mutual_followers":[...],"mutual_count":N,"reason_kind":"followed_by_mutuals"|"new_account"}`
 * Returns null when the active account is unknown or the WoT graph is not yet bootstrapped.
 */
fun NMPBridge.socialProofJson(activePubkey: String, targetPubkey: String): String? =
    if (activePubkey.isEmpty() || targetPubkey.isEmpty()) null
    else socialProofJsonImpl(activePubkey, targetPubkey)

// --- P0-F: ranked discover sections -------------------------------------------

/**
 * Return ranked discover sections for [activePubkey] from the WoT follow graph.
 * Returns: `[{"title":"...","reason":"...","profiles":[{"pubkey":"...","mutual_count":N}]}]`
 * Returns null when the active account is unknown or the WoT runtime is absent.
 */
fun NMPBridge.discoverSectionsJson(activePubkey: String): String? =
    if (activePubkey.isEmpty()) null
    else discoverSectionsJsonImpl(activePubkey)
