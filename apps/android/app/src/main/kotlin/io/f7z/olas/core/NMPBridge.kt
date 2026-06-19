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
    init {
        System.loadLibrary("nmp_app_olas")
    }

    private var appHandle: Long = 0L

    // Raw FlatBuffer frames from the update callback (for action-result decoding).
    private val _events = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val events: SharedFlow<ByteArray> = _events.asSharedFlow()

    // JSON-encoded KernelEvent strings delivered by the kernel event observer.
    private val _nostrEvents = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val nostrEvents: SharedFlow<String> = _nostrEvents.asSharedFlow()

    // JSON-encoded claimed_profiles array from snapshot frames.
    private val _claimedProfilesJson = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val claimedProfilesJson: SharedFlow<String> = _claimedProfilesJson.asSharedFlow()

    // JNI methods — implemented in nmp-app-olas/src/jni.rs
    private external fun nativeNew(): Long
    private external fun nativeFree(handle: Long)
    private external fun nativeRegister(handle: Long)
    private external fun nativeConsumeAllBuiltinProjections(handle: Long)
    private external fun nativeStart(handle: Long, storagePath: String)
    private external fun nativeStop(handle: Long)
    private external fun nativeSetUpdateListener(handle: Long, listener: UpdateListener?)
    private external fun nativeCreateAccount(handle: Long, name: String, username: String)
    private external fun nativeSeedDefaultRelays(handle: Long)
    private external fun nativeOpenSearchFeed(handle: Long, query: String, consumerId: String)
    private external fun nativeCloseSearchFeed(handle: Long, query: String, consumerId: String)
    private external fun nativeSignInNsec(handle: Long, nsec: String)
    private external fun nativeAddRelay(handle: Long, url: String, role: String)
    private external fun nativeOpenContactFeed(handle: Long, kindsJson: String)
    private external fun nativeOpenPhotoFeed(handle: Long, contactListOnly: Boolean)
    private external fun nativeDispatchAction(handle: Long, namespace: String, actionJson: String): String?
    private external fun nativeRegisterEventObserver(handle: Long, listener: EventObserverListener)
    private external fun nativeDecodeActionResults(handle: Long, frame: ByteArray): String?
    private external fun nativeDecodeClaimedProfiles(handle: Long, frame: ByteArray): String?
    private external fun nativeClaimProfile(handle: Long, pubkey: String, consumerId: String)
    private external fun nativeReleaseProfile(handle: Long, pubkey: String, consumerId: String)
    private external fun nativeBlossomUploadInputJson(filePath: String, contentType: String?, serverUrl: String?): String?
    private external fun nativePicturePostPublishJson(blossomResultJson: String, caption: String?, alt: String?, dim: String?): String?
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
        // Default relay set lives in Rust — no URLs hardcoded in Kotlin (D3).
        nativeSeedDefaultRelays(appHandle)
    }

    fun createAccount(name: String, username: String) =
        nativeCreateAccount(appHandle, name, username)

    fun openSearchFeed(query: String, consumerId: String = "olas.search") =
        nativeOpenSearchFeed(appHandle, query, consumerId)

    fun closeSearchFeed(query: String, consumerId: String = "olas.search") =
        nativeCloseSearchFeed(appHandle, query, consumerId)

    fun signInNsec(nsec: String) = nativeSignInNsec(appHandle, nsec)

    /** Following feed — kind 20 scoped to contact list. */
    fun openFollowingFeed() = nativeOpenContactFeed(appHandle, "[20]")

    /** Network feed — kind 20 global (unfiltered; WoT gap pending). */
    fun openNetworkFeed() = nativeOpenPhotoFeed(appHandle, false)

    fun loadOlderFeed(key: String) = nativeLoadOlderFeed(appHandle, key)

    fun dispatchAction(namespace: String, json: String): String? =
        nativeDispatchAction(appHandle, namespace, json)

    // --- Follow / Unfollow ----------------------------------------------------
    // followedPubkeys state comes from the Rust follow-graph projection (#7).
    // Do NOT add optimistic mutations here — Rust is the single source of truth.

    fun follow(pubkey: String) = dispatchAction("nmp.follow", """{"pubkey":"$pubkey"}""")
    fun unfollow(pubkey: String) = dispatchAction("nmp.unfollow", """{"pubkey":"$pubkey"}""")

    fun claimProfile(pubkey: String, consumerId: String = "olas.profile") =
        nativeClaimProfile(appHandle, pubkey, consumerId)
    fun releaseProfile(pubkey: String, consumerId: String = "olas.profile") =
        nativeReleaseProfile(appHandle, pubkey, consumerId)

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
        // Decode claimed_profiles on every frame and broadcast to OlasProfileHost.
        runCatching { nativeDecodeClaimedProfiles(appHandle, frame) }
            .getOrNull()?.let { _claimedProfilesJson.tryEmit(it) }

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
    fun picturePostPublishJson(blossomResultJson: String, caption: String?, alt: String?, dim: String?): String? =
        nativePicturePostPublishJson(blossomResultJson, caption, alt, dim)

    fun walletConnect(uri: String) = nativeWalletConnect(appHandle, uri)

    fun lifecycleForeground() = nativeLifecycleForeground(appHandle)
    fun lifecycleBackground() = nativeLifecycleBackground(appHandle)
}
