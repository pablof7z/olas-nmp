package org.nmp.registry

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Wire types live in ExternalSignerWire.kt; the Amber-specific Intent/URI/
// permissions encoding and result extraction live in AmberIntentCodec.kt.
// The three files are vendored together as one unit (ADR-0048 Stage 2):
// byte-identical copies except the package line, see VendorDriftGateTest.

private val bridgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    classDiscriminator = "kind"
}

/**
 * D7 host adapter for the `external_signer` capability namespace.
 *
 * Receives fully-built `ExternalSignerRequest` objects from Rust, fires
 * the right OS IPC mechanism (Intent round-trip or ContentResolver
 * fast-path), and reports raw results back via `onResult` — it decides
 * nothing.
 *
 * ## Transport selection (D7 — mechanical, not policy)
 *
 * | Condition | Mechanism |
 * |---|---|
 * | `forceInteractive == true` | Intent |
 * | method is in `grantedPermissions` and `signerPackage` known | ContentResolver |
 * | otherwise | Intent |
 *
 * A ContentResolver returning `null` is reported as `Unavailable`; Rust
 * will re-issue the op with `force_interactive: true` to fall back to the
 * Intent path (D7 — the host never decides to retry).
 *
 * ## Lifecycle note
 *
 * The bridge registers an Activity Result launcher. Register it in
 * `Activity.onCreate` (before first `onStart`) via [register], not later.
 * Call [unregister] in `onDestroy` to release the launcher.
 *
 * @param activity The host activity (needed for `registerForActivityResult`).
 * @param onResult Called with the serialised `ExternalSignerResponse` JSON.
 *   Route this back to the kernel via `KernelBridge.deliverSignerResponse`.
 */
class ExternalSignerCapabilityBridge(
    private val activity: ComponentActivity,
    private val onResult: (responseJson: String) -> Unit,
) {

    // ── In-flight tracking ─────────────────────────────────────────────

    /** correlation_id of the request that is currently awaiting an Intent result. */
    @Volatile
    private var pendingCorrelationId: String? = null

    /** Method of the in-flight Intent (needed to build the response). */
    @Volatile
    private var pendingMethod: String? = null

    // ── Activity Result launcher ───────────────────────────────────────

    /**
     * Activity Result launcher registered for the `nostrsigner:` Intent.
     *
     * Android delivers the signer's reply as `Activity.RESULT_OK` with
     * extras (Amber `IntentUtils.sendResult`):
     * - `"result"` — the raw string value. For `sign_event` this is the
     *   SIGNATURE HEX ONLY; for `get_public_key` the pubkey; for
     *   encrypt/decrypt the ciphertext/plaintext.
     * - `"event"` — the full signed-event JSON (`sign_event` replies).
     *   Rust's `parse_signed_event_response` verifies id + schnorr sig on
     *   the complete event, so this extra — not `result` — is the value a
     *   `sign_event` reply must carry (Stage-4 emulator finding #3); see
     *   `selectAmberResultValue` in AmberIntentCodec.kt.
     * - `"package"` — the signer app's package name (present on
     *   `get_public_key` replies; Amber-specific).
     * - `"rejected"` — boolean; Amber returns `RESULT_OK` with this flag
     *   (instead of `RESULT_CANCELED`) when the user rejects with a
     *   remember-choice. Reported as `Rejected`.
     *
     * `RESULT_CANCELED` means the user navigated back without approving —
     * reported as `Rejected`.
     */
    private var launcher: ActivityResultLauncher<Intent>? = null

    /**
     * Register the Activity Result launcher. Call from `Activity.onCreate`
     * BEFORE first `onStart`. Safe to call multiple times; subsequent calls
     * are no-ops.
     */
    fun register() {
        if (launcher != null) return
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val correlationId = pendingCorrelationId ?: return@registerForActivityResult
            pendingCorrelationId = null
            val method = pendingMethod ?: "unknown"
            pendingMethod = null

            val response = if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val rawResult = selectAmberResultValue(
                    method = method,
                    eventExtra = data?.getStringExtra("event"),
                    resultExtra = data?.getStringExtra("result"),
                )
                val signerPackage = data?.getStringExtra("package")
                if (data?.getBooleanExtra("rejected", false) == true) {
                    // Amber reports remembered rejections as RESULT_OK +
                    // `rejected: true` (not RESULT_CANCELED).
                    ExternalSignerResponse(
                        correlationId = correlationId,
                        outcome = ExternalSignerOutcome.Rejected(
                            reason = "signer rejected the request",
                        ),
                    )
                } else if (rawResult != null) {
                    ExternalSignerResponse(
                        correlationId = correlationId,
                        outcome = ExternalSignerOutcome.Ok(result = rawResult),
                        signerPackage = signerPackage.takeIf {
                            method == "get_public_key" && it != null
                        },
                    )
                } else {
                    ExternalSignerResponse(
                        correlationId = correlationId,
                        outcome = ExternalSignerOutcome.Unavailable(
                            reason = "signer returned no result",
                        ),
                    )
                }
            } else {
                ExternalSignerResponse(
                    correlationId = correlationId,
                    outcome = ExternalSignerOutcome.Rejected(
                        reason = "user cancelled",
                    ),
                )
            }
            onResult(bridgeJson.encodeToString(response))
        }
    }

    /**
     * Unregister the launcher. Call from `Activity.onDestroy`.
     * Safe to call when not registered.
     */
    fun unregister() {
        launcher?.unregister()
        launcher = null
    }

    // ── Dispatch ───────────────────────────────────────────────────────

    /**
     * Handle an `ExternalSignerRequest` built by Rust.
     *
     * Selects the transport path mechanically from `forceInteractive` +
     * `grantedPermissions`, then dispatches. D7: no policy decisions here.
     *
     * For the gallery showcase this is called with a stateless callback
     * wired to `onResult`. For Chirp it is wired into the kernel via
     * `nativeDeliverSignerResponse` (see `KernelBridge`).
     */
    fun handle(request: ExternalSignerRequest) {
        if (shouldUseContentResolver(request)) {
            dispatchContentResolver(request)
        } else {
            dispatchIntent(request)
        }
    }

    /**
     * Parse a raw `ExternalSignerRequest` JSON string and dispatch.
     * Called from the capability callback registered with the kernel.
     *
     * D6: malformed JSON is silently dropped (no crash); it degrades to
     * timeout on the Rust side (the correlation_id sender is never resolved).
     */
    fun handleJson(requestJson: String) {
        val request = try {
            bridgeJson.decodeFromString<ExternalSignerRequest>(requestJson)
        } catch (_: Exception) {
            return // D6: malformed — degrade to timeout
        }
        handle(request)
    }

    // ── Intent path ───────────────────────────────────────────────────

    private fun dispatchIntent(request: ExternalSignerRequest) {
        val intent = buildAmberSignerIntent(request)

        val existing = pendingCorrelationId
        if (existing != null) {
            reportUnavailable(
                request.correlationId,
                "external signer Intent already pending for correlation $existing",
            )
            return
        }

        pendingCorrelationId = request.correlationId
        pendingMethod = request.method

        val l = launcher
        if (l != null) {
            l.launch(intent)
        } else {
            // Launcher not registered — report Unavailable so Rust can toast.
            pendingCorrelationId = null
            pendingMethod = null
            reportUnavailable(request.correlationId, "capability bridge not registered")
        }
    }

    // ── ContentResolver fast-path ─────────────────────────────────────

    private fun dispatchContentResolver(request: ExternalSignerRequest) {
        val pkg = request.signerPackage ?: run {
            reportUnavailable(request.correlationId, "signer package unknown for ContentResolver path")
            return
        }
        val method = request.method.toNostrSignerMethod()
        val authority = "$pkg.$method"
        val uri = Uri.parse("content://$authority")

        // NIP-55 ContentResolver call: the selection string carries the payload
        // and optional counterparty and current_user fields.
        val selectionArgs = arrayOf(
            request.payload,
            request.counterparty ?: "",
            request.currentUser ?: "",
        )

        try {
            val cursor = activity.contentResolver.query(
                uri,
                null,  // projection
                null,  // selection (Amber uses selectionArgs directly)
                selectionArgs,
                null,  // sortOrder
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val resultCol = c.getColumnIndex("result")
                    val rawResult = if (resultCol >= 0) c.getString(resultCol) else null
                    if (rawResult != null) {
                        val resp = ExternalSignerResponse(
                            correlationId = request.correlationId,
                            outcome = ExternalSignerOutcome.Ok(result = rawResult),
                        )
                        onResult(bridgeJson.encodeToString(resp))
                    } else {
                        // null result = silently-revoked permission. Report Unavailable;
                        // Rust re-issues with force_interactive = true.
                        reportUnavailable(request.correlationId, "ContentResolver returned null result")
                    }
                } else {
                    reportUnavailable(request.correlationId, "ContentResolver returned empty cursor")
                }
            } ?: reportUnavailable(request.correlationId, "ContentResolver returned null cursor")
        } catch (e: Exception) {
            reportUnavailable(request.correlationId, "ContentResolver error: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun reportUnavailable(correlationId: String, reason: String) {
        val resp = ExternalSignerResponse(
            correlationId = correlationId,
            outcome = ExternalSignerOutcome.Unavailable(reason = reason),
        )
        onResult(bridgeJson.encodeToString(resp))
    }

    companion object {
        /**
         * Detect installed Nostr signer apps using the PackageManager.
         * Convenience wrapper over [detectInstalledSigners].
         */
        fun detect(context: Context): List<NostrSignerInfo> =
            detectInstalledSigners(context.packageManager)
    }
}
