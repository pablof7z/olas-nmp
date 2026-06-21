package io.f7z.olas.core

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton bridge to the nmp_app_olas native library.
 *
 * All calls are pass-through to Rust (JNI) — no business logic here.
 * Update delivery is push-only: Rust invokes onUpdate() / onEvent() on the
 * kernel's listener thread; Kotlin collects via [events] / [nostrEvents] SharedFlow.
 */
object NMPBridge {
    private const val SETTINGS_PREFS = "olas_settings"
    private const val KEY_STORED_NSEC = "stored_nsec"
    private const val FOLLOWING_FEED_KEY = "olas.following_feed"
    private const val NETWORK_FEED_KEY = "olas.network_feed"
    private val photoFeedKeys = ConcurrentHashMap.newKeySet<String>().apply {
        add(FOLLOWING_FEED_KEY)
        add(NETWORK_FEED_KEY)
    }

    init {
        System.loadLibrary("nmp_app_olas")
    }

    private var appHandle: Long = 0L
    private var appContext: Context? = null

    // Raw FlatBuffer frames from the update callback (for action-result decoding).
    private val _events = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val events: SharedFlow<ByteArray> = _events.asSharedFlow()

    // JSON-encoded KernelEvent strings delivered by the kernel event observer.
    private val _nostrEvents = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 512)
    val nostrEvents: SharedFlow<String> = _nostrEvents.asSharedFlow()

    // JSON array of claimed profile entries, emitted on each snapshot frame that carries them.
    private val _claimedProfilesJson = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val claimedProfilesJson: SharedFlow<String> = _claimedProfilesJson.asSharedFlow()

    // JSON arrays of Rust-owned photo-feed rows, keyed by feed projection key.
    private val _photoFeedsJson = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val photoFeedsJson: SharedFlow<Pair<String, String>> = _photoFeedsJson.asSharedFlow()

    @Volatile
    var activeAccountPubkey: String? = null
        private set

    private val _activeAccountPubkeyFlow = MutableStateFlow<String?>(null)
    val activeAccountPubkeyFlow: StateFlow<String?> = _activeAccountPubkeyFlow.asStateFlow()

    // JNI methods — implemented in nmp-app-olas/src/jni.rs
    private external fun nativeNew(): Long
    private external fun nativeFree(handle: Long)
    private external fun nativeRegister(handle: Long)
    private external fun nativeConsumeAllBuiltinProjections(handle: Long)
    private external fun nativeStart(handle: Long, storagePath: String)
    private external fun nativeStop(handle: Long)
    private external fun nativeSetUpdateListener(handle: Long, listener: UpdateListener?)
    private external fun nativeCreateAccount(handle: Long, name: String, username: String)
    private external fun nativeOpenSearchFeed(handle: Long, query: String, consumerId: String)
    private external fun nativeCloseSearchFeed(handle: Long, query: String, consumerId: String)
    private external fun nativeOpenAuthorPhotoFeed(handle: Long, pubkey: String, consumerId: String)
    private external fun nativeCloseAuthorPhotoFeed(handle: Long, pubkey: String, consumerId: String)
    private external fun nativeSignInNsec(handle: Long, nsec: String)
    private external fun nativeAddRelay(handle: Long, url: String, role: String)
    private external fun nativeOpenPhotoFeed(handle: Long, contactListOnly: Boolean, consumerId: String)
    private external fun nativeProfileJson(handle: Long, eventJson: String): String?
    private external fun nativeNotificationJson(handle: Long, eventJson: String): String?
    private external fun nativeContactListPubkeysJson(
        handle: Long,
        eventJson: String,
        activePubkey: String,
    ): String?
    private external fun nativeDefaultRelaysJson(): String?
    private external fun nativeDispatchAction(handle: Long, namespace: String, actionJson: String): String?
    private external fun nativeRegisterEventObserver(handle: Long, listener: EventObserverListener)
    private external fun nativeDecodeActionResults(handle: Long, frame: ByteArray): String?
    private external fun nativeDecodeActiveAccount(handle: Long, frame: ByteArray): String?
    private external fun nativeBlossomUploadInputJson(filePath: String, contentType: String?, serverUrl: String?): String?
    private external fun nativeReactActionJson(targetEventId: String, targetAuthorPubkey: String?): String?
    private external fun nativeZapActionJson(recipientPubkey: String, targetEventId: String?, amountMsats: Long, comment: String?): String?
    private external fun nativeBolt11AmountSats(bolt11: String): Long
    private external fun nativeBookmarkEventActionJson(accountPubkey: String, eventId: String): String?
    private external fun nativeLocationGeohash4(latitude: Double, longitude: Double): String?
    private external fun nativePicturePostPublishJson(
        blossomResultJson: String,
        caption: String?,
        alt: String?,
        dim: String?,
        geohash: String?,
    ): String?
    private external fun nativeLoadOlderFeed(handle: Long, key: String)
    private external fun nativeLifecycleForeground(handle: Long)
    private external fun nativeLifecycleBackground(handle: Long)
    private external fun nativeWalletConnect(handle: Long, uri: String)
    private external fun nativeSetStoragePath(handle: Long, path: String): Int
    private external fun nativeDecodeKind20EventJson(handle: Long, eventJson: String): String?
    private external fun nativeDecodeKind0EventJson(handle: Long, eventJson: String): String?
    private external fun nativeBolt11AmountMsats(bolt11: String): Long
    private external fun nativeComputeGeohash(lat: Double, lon: Double, precision: Int): String?
    private external fun nativeBuildZapActionJson(eventId: String, sats: Long): String?
    private external fun nativeFilterCatalogJson(): String?
    private external fun nativeMediaUploadConfigJson(): String?
    private external fun nativePickerConfigJson(): String?
    private external fun nativeSettingsCatalogJson(): String?
    private external fun nativeOnboardingStepsJson(): String?
    private external fun nativeComposeStepsJson(): String?
    private external fun nativeBlossomServerUrlGet(handle: Long): String?
    private external fun nativeBlossomServerUrlSet(handle: Long, url: String)
    private external fun nativeFeedModeGet(handle: Long): String?
    private external fun nativeFeedModeSet(handle: Long, mode: String)
    private external fun nativeClaimProfile(handle: Long, pubkey: String, consumerId: String)
    private external fun nativeReleaseProfile(handle: Long, pubkey: String, consumerId: String)
    private external fun nativeDecodeClaimedProfiles(handle: Long, frame: ByteArray): String?
    private external fun nativeDecodePhotoFeed(handle: Long, frame: ByteArray, key: String): String?
    private external fun nativeCurrentPhotoFeed(handle: Long, key: String): String?
    private external fun nativeWotPresetGet(handle: Long): String?
    private external fun nativeWotPresetSet(handle: Long, preset: String)

    /** Called from Rust on the kernel's listener thread (raw FlatBuffer frames). */
    interface UpdateListener {
        fun onUpdate(data: ByteArray)
    }

    /** Called from Rust on the kernel's actor thread (JSON-encoded KernelEvent). */
    interface EventObserverListener {
        fun onEvent(json: String)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (appHandle != 0L) return
        appHandle = nativeNew()
        if (appHandle == 0L) return
        nativeRegister(appHandle)
        // ADR-0053: declare projection-consumption intent before start so the
        // kernel emits built-in projections (incl. `action_results`).
        nativeConsumeAllBuiltinProjections(appHandle)
        val storageDir = File(context.filesDir, "nmp").also { it.mkdirs() }
        nativeSetStoragePath(appHandle, storageDir.absolutePath)
        nativeSetUpdateListener(appHandle, object : UpdateListener {
            override fun onUpdate(data: ByteArray) {
                _events.tryEmit(data)
                handleSnapshotFrame(data)
            }
        })
        nativeRegisterEventObserver(appHandle, object : EventObserverListener {
            override fun onEvent(json: String) {
                _nostrEvents.tryEmit(json)
            }
        })
        nativeStart(appHandle, storageDir.absolutePath)
        // Re-inject stored nsec — NMP's keyring capability is not wired on Android, so
        // restore_active_session leaves the account absent after a process restart.
        val storedNsec = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STORED_NSEC, null)
        if (!storedNsec.isNullOrBlank()) {
            nativeSignInNsec(appHandle, storedNsec)
        }
        // blossom.band (nostr.build CDN) stores files for any NIP-98-authenticated key.
        nativeBlossomServerUrlSet(appHandle, "https://blossom.band")
    }

    fun createAccount(name: String, username: String) =
        nativeCreateAccount(appHandle, name, username)

    fun openSearchFeed(query: String, consumerId: String = "olas.search") {
        photoFeedKeys.add(consumerId)
        nativeOpenSearchFeed(appHandle, query, consumerId)
    }

    fun closeSearchFeed(query: String, consumerId: String = "olas.search") {
        nativeCloseSearchFeed(appHandle, query, consumerId)
        photoFeedKeys.remove(consumerId)
    }

    fun authorPhotoFeedKey(pubkey: String): String = "olas.author.$pubkey"

    fun openAuthorPhotoFeed(pubkey: String, consumerId: String = authorPhotoFeedKey(pubkey)) {
        photoFeedKeys.add(consumerId)
        nativeOpenAuthorPhotoFeed(appHandle, pubkey, consumerId)
    }

    fun closeAuthorPhotoFeed(pubkey: String, consumerId: String = authorPhotoFeedKey(pubkey)) {
        nativeCloseAuthorPhotoFeed(appHandle, pubkey, consumerId)
        photoFeedKeys.remove(consumerId)
    }

    fun signInNsec(nsec: String) {
        appContext?.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_STORED_NSEC, nsec)?.apply()
        nativeSignInNsec(appHandle, nsec)
    }

    fun signOut() {
        appContext?.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_STORED_NSEC)?.apply()
    }

    /** Following feed — kind 20 scoped to contact list. */
    fun openFollowingFeed() = nativeOpenPhotoFeed(appHandle, true, FOLLOWING_FEED_KEY)

    /** Network feed — kind 20 global; Rust applies the active WoT preset per event. */
    fun openNetworkFeed() = nativeOpenPhotoFeed(appHandle, false, NETWORK_FEED_KEY)

    fun currentPhotoFeedJson(key: String): String? = nativeCurrentPhotoFeed(appHandle, key)

    fun profileJson(raw: String): String? = nativeProfileJson(appHandle, raw)

    fun notificationJson(raw: String): String? = nativeNotificationJson(appHandle, raw)

    fun contactListPubkeysJson(raw: String, activePubkey: String): String? =
        nativeContactListPubkeysJson(appHandle, raw, activePubkey)

    fun defaultRelaysJson(): String = nativeDefaultRelaysJson() ?: "[]"

    fun wotPreset(): String = nativeWotPresetGet(appHandle) ?: "balanced"

    fun setWotPreset(preset: String) {
        nativeWotPresetSet(appHandle, preset.lowercase())
    }

    fun loadOlderFeed(key: String) = nativeLoadOlderFeed(appHandle, key)

    fun dispatchAction(namespace: String, json: String): String? =
        nativeDispatchAction(appHandle, namespace, json)

    // --- Follow / Unfollow ----------------------------------------------------
    // followedPubkeys state comes from the Rust follow-graph projection (#7).
    // Do NOT add optimistic mutations here — Rust is the single source of truth.

    fun follow(pubkey: String) = dispatchAction("nmp.follow", """{"pubkey":"$pubkey"}""")
    fun unfollow(pubkey: String) = dispatchAction("nmp.unfollow", """{"pubkey":"$pubkey"}""")

    // --- Post interactions ----------------------------------------------------

    fun reactTo(post: PhotoPost): String? {
        val input = nativeReactActionJson(post.id, post.authorPubkey) ?: return null
        return dispatchAction("nmp.nip25.react", input)
    }

    fun bookmarkEvent(post: PhotoPost, add: Boolean): String? {
        val account = activeAccountPubkey ?: return null
        val input = nativeBookmarkEventActionJson(account, post.id) ?: return null
        val namespace = if (add) "nmp.nip51.add_bookmark" else "nmp.nip51.remove_bookmark"
        return dispatchAction(namespace, input)
    }

    fun zapPost(post: PhotoPost, amountSats: Long, comment: String? = null): String? {
        val input = nativeZapActionJson(
            post.authorPubkey,
            post.id,
            amountSats * 1_000,
            comment,
        ) ?: return null
        return dispatchAction("nmp.nip57.zap", input)
    }

    fun bolt11AmountSats(bolt11: String): Long? =
        nativeBolt11AmountSats(bolt11).takeIf { it > 0 }

    // --- Relay management -----------------------------------------------------

    fun addRelay(url: String, role: String) = nativeAddRelay(appHandle, url, role)

    // --- Async-completing actions (await terminal by correlation_id) ----------

    /** Terminal of an async action: status + optional structured result + optional reason. */
    data class ActionTerminal(val status: String, val resultJson: String, val reason: String = "") {
        val succeeded: Boolean get() = status != "failed"
    }

    /** Waiters suspended on an async action terminal, keyed by correlation_id. */
    private val actionResultWaiters = ConcurrentHashMap<String, CompletableDeferred<ActionTerminal?>>()

    /**
     * Parse a snapshot FlatBuffer frame by calling into Rust to decode the
     * action_results projection.  Resolves any awaiter whose correlation_id
     * appears in the result array.
     */
    private fun handleSnapshotFrame(frame: ByteArray) {
        runCatching { nativeDecodeActiveAccount(appHandle, frame) }
            .getOrNull()
            ?.let { raw ->
                runCatching { JSONObject(raw).optString("pubkey") }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { activeAccountPubkey = it; _activeAccountPubkeyFlow.value = it }
            }

        runCatching { nativeDecodeClaimedProfiles(appHandle, frame) }
            .getOrNull()
            ?.let { json -> _claimedProfilesJson.tryEmit(json) }

        for (key in photoFeedKeys) {
            runCatching { nativeDecodePhotoFeed(appHandle, frame, key) }
                .getOrNull()
                ?.let { json -> _photoFeedsJson.tryEmit(key to json) }
        }

        if (actionResultWaiters.isEmpty()) return
        val json = runCatching { nativeDecodeActionResults(appHandle, frame) }
            .getOrNull() ?: return
        android.util.Log.d("OlasActionResult", "action_results JSON: $json")
        val rows = runCatching { JSONArray(json) }.getOrNull() ?: return
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val cid = row.optString("correlation_id").takeIf { it.isNotEmpty() } ?: continue
            val waiter = actionResultWaiters.remove(cid) ?: continue
            val status = row.optString("status", "published")
            val resultJson = if (row.isNull("result")) "null" else row.get("result").toString()
            val reason = row.optString("error", "")
            android.util.Log.d("OlasActionResult", "cid=$cid status=$status error=$reason result=$resultJson")
            waiter.complete(ActionTerminal(status, resultJson, reason))
        }
    }

    /**
     * Dispatch an async-completing action and suspend until its terminal row
     * appears in `projections.action_results`. Returns the terminal, or null if
     * dispatch was rejected synchronously. D8: the snapshot stream wakes the
     * awaiter — no polling.
     */
    suspend fun dispatchAndAwaitResult(namespace: String, json: String): ActionTerminal? {
        val ret = nativeDispatchAction(appHandle, namespace, json) ?: return null
        val cid = runCatching { JSONObject(ret).optString("correlation_id") }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val deferred = CompletableDeferred<ActionTerminal?>()
        actionResultWaiters[cid] = deferred
        return deferred.await()
    }

    /** Build the nmp.blossom.upload action input JSON in Rust (no logic in Kotlin). */
    fun blossomUploadInputJson(filePath: String, mime: String?, serverUrl: String?): String? =
        nativeBlossomUploadInputJson(filePath, mime, serverUrl)

    /** Build the nmp.publish (PublishRaw) kind:20 input JSON in Rust from a Blossom result. */
    fun picturePostPublishJson(
        blossomResultJson: String,
        caption: String?,
        alt: String?,
        dim: String?,
        geohash: String?,
    ): String? = nativePicturePostPublishJson(blossomResultJson, caption, alt, dim, geohash)

    /** Native supplies the raw OS location fix; Rust owns geohash precision/encoding. */
    fun locationGeohash4(latitude: Double, longitude: Double): String? =
        nativeLocationGeohash4(latitude, longitude)

    fun walletConnect(uri: String) = nativeWalletConnect(appHandle, uri)

    fun lifecycleForeground() = nativeLifecycleForeground(appHandle)
    fun lifecycleBackground() = nativeLifecycleBackground(appHandle)

    // --- New FFI helpers ------------------------------------------------------

    fun decodeKind20EventJson(eventJson: String): String? = nativeDecodeKind20EventJson(appHandle, eventJson)
    fun decodeKind0EventJson(eventJson: String): String? = nativeDecodeKind0EventJson(appHandle, eventJson)
    fun bolt11AmountMsats(bolt11: String): Long = nativeBolt11AmountMsats(bolt11)
    fun computeGeohash(lat: Double, lon: Double, precision: Int = 6): String? = nativeComputeGeohash(lat, lon, precision)
    fun buildZapActionJson(eventId: String, sats: Long): String? = nativeBuildZapActionJson(eventId, sats)
    fun filterCatalogJson(): String? = nativeFilterCatalogJson()
    fun mediaUploadConfigJson(): String? = nativeMediaUploadConfigJson()
    fun pickerConfigJson(): String? = nativePickerConfigJson()
    fun settingsCatalogJson(): String? = nativeSettingsCatalogJson()
    fun onboardingStepsJson(): String? = nativeOnboardingStepsJson()
    fun composeStepsJson(): String? = nativeComposeStepsJson()
    fun blossomServerUrl(): String = nativeBlossomServerUrlGet(appHandle) ?: ""
    fun setBlossomServerUrl(url: String) = nativeBlossomServerUrlSet(appHandle, url)
    fun feedMode(): String = nativeFeedModeGet(appHandle) ?: "network"
    fun setFeedMode(mode: String) = nativeFeedModeSet(appHandle, mode)

    // --- Profile claiming -----------------------------------------------------

    fun claimProfile(pubkey: String, consumerId: String) =
        nativeClaimProfile(appHandle, pubkey, consumerId)

    fun releaseProfile(pubkey: String, consumerId: String) =
        nativeReleaseProfile(appHandle, pubkey, consumerId)
}
