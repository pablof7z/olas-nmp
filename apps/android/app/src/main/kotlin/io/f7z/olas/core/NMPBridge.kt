package io.f7z.olas.core

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private const val KEY_WOT_PRESET = "wotPreset"

    init {
        System.loadLibrary("nmp_app_olas")
    }

    private var appHandle: Long = 0L
    private var appContext: Context? = null

    // Raw FlatBuffer frames from the update callback (for action-result decoding).
    private val _events = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val events: SharedFlow<ByteArray> = _events.asSharedFlow()

    // JSON-encoded KernelEvent strings delivered by the kernel event observer.
    private val _nostrEvents = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val nostrEvents: SharedFlow<String> = _nostrEvents.asSharedFlow()

    @Volatile
    var activeAccountPubkey: String? = null
        private set

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
    private external fun nativeSignInNsec(handle: Long, nsec: String)
    private external fun nativeAddRelay(handle: Long, url: String, role: String)
    private external fun nativeOpenContactFeed(handle: Long, kindsJson: String)
    private external fun nativeOpenPhotoFeed(handle: Long, contactListOnly: Boolean)
    private external fun nativeFilterPhotoPostJson(
        handle: Long,
        eventJson: String,
        contactListOnly: Boolean,
        wotPreset: String,
    ): String?
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
        // Wire the update callback: emit raw FlatBuffer frames to _events and
        // parse action_results in each frame.
        nativeSetUpdateListener(appHandle, object : UpdateListener {
            override fun onUpdate(data: ByteArray) {
                _events.tryEmit(data)
                handleSnapshotFrame(data)
            }
        })
        // Wire the kernel event observer: emit decoded JSON strings to _nostrEvents.
        nativeRegisterEventObserver(appHandle, object : EventObserverListener {
            override fun onEvent(json: String) {
                _nostrEvents.tryEmit(json)
            }
        })
        nativeStart(appHandle, storageDir.absolutePath)
    }

    fun createAccount(name: String, username: String) =
        nativeCreateAccount(appHandle, name, username)

    fun openSearchFeed(query: String, consumerId: String = "olas.search") =
        nativeOpenSearchFeed(appHandle, query, consumerId)

    fun closeSearchFeed(query: String, consumerId: String = "olas.search") =
        nativeCloseSearchFeed(appHandle, query, consumerId)

    fun signInNsec(nsec: String) = nativeSignInNsec(appHandle, nsec)

    /** Following feed — kind 20 scoped to contact list. */
    fun openFollowingFeed() = nativeOpenPhotoFeed(appHandle, true)

    /** Network feed — kind 20 global; Rust applies the active WoT preset per event. */
    fun openNetworkFeed() = nativeOpenPhotoFeed(appHandle, false)

    fun photoPostJson(raw: String, mode: FeedMode): String? =
        nativeFilterPhotoPostJson(appHandle, raw, mode == FeedMode.FOLLOWING, wotPreset())

    fun profileJson(raw: String): String? = nativeProfileJson(appHandle, raw)

    fun notificationJson(raw: String): String? = nativeNotificationJson(appHandle, raw)

    fun contactListPubkeysJson(raw: String, activePubkey: String): String? =
        nativeContactListPubkeysJson(appHandle, raw, activePubkey)

    fun defaultRelaysJson(): String = nativeDefaultRelaysJson() ?: "[]"

    fun wotPreset(): String =
        appContext
            ?.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_WOT_PRESET, "balanced")
            ?: "balanced"

    fun setWotPreset(preset: String) {
        appContext
            ?.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_WOT_PRESET, preset.lowercase())
            ?.apply()
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

    /** Terminal of an async action: status + optional structured result. */
    data class ActionTerminal(val status: String, val resultJson: String) {
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
                    ?.let { activeAccountPubkey = it }
            }

        if (actionResultWaiters.isEmpty()) return
        val json = runCatching { nativeDecodeActionResults(appHandle, frame) }
            .getOrNull() ?: return
        val rows = runCatching { JSONArray(json) }.getOrNull() ?: return
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val cid = row.optString("correlation_id").takeIf { it.isNotEmpty() } ?: continue
            val waiter = actionResultWaiters.remove(cid) ?: continue
            val status = row.optString("status", "published")
            val resultJson = if (row.isNull("result")) "null" else row.get("result").toString()
            waiter.complete(ActionTerminal(status, resultJson))
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
}
