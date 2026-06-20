package org.nmp.registry

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Wire types mirroring nmp-signer-iface ExternalSignerRequest/Response ──────
//
// Vendored with ExternalSignerCapabilityBridge.kt + AmberIntentCodec.kt as one
// unit (ADR-0048 Stage 2): byte-identical copies except the package line, see
// VendorDriftGateTest.

/**
 * Mirror of `ExternalSignerRequest` from `nmp-signer-iface`.
 *
 * Rust builds this and serialises it as `CapabilityRequest.payload_json`.
 * The Kotlin host fires it and reports the raw result — it decides nothing (D7).
 */
@Serializable
data class ExternalSignerRequest(
    @SerialName("correlation_id") val correlationId: String,
    val method: String,
    val payload: String,
    @SerialName("current_user") val currentUser: String? = null,
    val counterparty: String? = null,
    val permissions: List<Nip55Permission> = emptyList(),
    @SerialName("granted_permissions") val grantedPermissions: List<Nip55Permission> = emptyList(),
    @SerialName("signer_package") val signerPackage: String? = null,
    @SerialName("force_interactive") val forceInteractive: Boolean = false,
)

/** Mirror of `Nip55Permission` from `nmp-signer-iface`. */
@Serializable
data class Nip55Permission(val kind: String)

/**
 * Mirror of `ExternalSignerResponse` from `nmp-signer-iface`.
 *
 * The host fills this and hands it back to Rust via `deliverResponse`.
 * D7: raw results only, no interpretation.
 */
@Serializable
data class ExternalSignerResponse(
    @SerialName("correlation_id") val correlationId: String,
    val outcome: ExternalSignerOutcome,
    @SerialName("signer_package") val signerPackage: String? = null,
)

/** Wire shape for `ExternalSignerOutcome` (tagged by `kind`). */
@Serializable
sealed class ExternalSignerOutcome {
    @Serializable
    @SerialName("ok")
    data class Ok(val result: String) : ExternalSignerOutcome()

    @Serializable
    @SerialName("rejected")
    data class Rejected(val reason: String) : ExternalSignerOutcome()

    @Serializable
    @SerialName("unavailable")
    data class Unavailable(val reason: String) : ExternalSignerOutcome()

    @Serializable
    @SerialName("signer_error")
    data class SignerError(val reason: String) : ExternalSignerOutcome()
}

// ── Known signer descriptors ──────────────────────────────────────────────────

/**
 * Describes one locally-detectable Nostr signer app.
 *
 * Android detection: `PackageManager.queryIntentActivities` on the
 * `nostrsigner:` scheme. This is the Android analogue of the SwiftUI
 * `NostrSignerDetector.knownSigners` list.
 *
 * All package names listed here MUST also appear in the app's
 * `<queries>` block in `AndroidManifest.xml` — see the comment at the
 * top of the manifest. Without the `<queries>` declaration Android 11+
 * (API 30+) returns an empty result even when the app is installed.
 */
data class NostrSignerInfo(
    /** Display name shown in the login-block card (e.g. "Amber"). */
    val displayName: String,
    /**
     * The `nostrsigner:` scheme used for Intent dispatch.
     * Amber registers `nostrsigner`; future signers may differ.
     */
    val intentScheme: String,
    /**
     * ContentProvider authority prefix for the fast-path (background)
     * queries after the permission batch is granted. For Amber:
     * `com.greenart7c3.nostrsigner`. The full authority per-method is
     * `"$contentAuthority.<METHOD>"`, e.g.
     * `"com.greenart7c3.nostrsigner.sign_event"`.
     *
     * `null` means this signer supports the Intent path only.
     */
    val contentAuthority: String? = null,
    /**
     * The Android package name passed to `KernelBridge.signInNip55` as the
     * `signer_package` wire field. For Amber the package name and
     * contentAuthority coincide (`com.greenart7c3.nostrsigner`), but the
     * two fields are logically distinct: contentAuthority is the
     * ContentProvider namespace; packageName is the APK identifier used for
     * Intent routing and for the `signer_package` field in the Rust wire.
     *
     * Defaults to [contentAuthority] when null (backward-compatible for
     * signers where the two values are identical).
     */
    val packageName: String? = null,
    /**
     * Human-readable "not installed" hint for the UI.
     */
    val installHint: String = "Install $displayName for one-tap sign-in",
)

// `KNOWN_NOSTR_SIGNERS` is GENERATED from the Rust catalog
// (nmp_core::signer_catalog) into the sibling file `KnownSigners.generated.kt`
// in this same package — `detectInstalledSigners` below references it directly.
// Edit the catalog + re-run `nmp gen signer-catalog`, never this file (#1493 P9).

// ── Package-manager-based detection ──────────────────────────────────────────

/**
 * Probes `PackageManager` for installed Nostr signer apps.
 *
 * Returns only signers whose `intentScheme` can be resolved by an
 * installed app. This mirrors the iOS `NostrSignerDetector.detect()`
 * approach (`UIApplication.canOpenURL`) but uses the Android
 * `PackageManager.queryIntentActivities` API instead.
 *
 * MUST be called on the main thread (same constraint as the iOS counterpart).
 *
 * ## AndroidManifest requirement
 *
 * Add the following `<queries>` block to your app's manifest:
 * ```xml
 * <queries>
 *     <intent>
 *         <action android:name="android.intent.action.VIEW" />
 *         <data android:scheme="nostrsigner" />
 *     </intent>
 * </queries>
 * ```
 * Without this declaration Android 11+ (API 30+) returns an empty list
 * even when Amber is installed.
 */
fun detectInstalledSigners(packageManager: PackageManager): List<NostrSignerInfo> {
    return KNOWN_NOSTR_SIGNERS.filter { signer ->
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("${signer.intentScheme}://"))
        @Suppress("DEPRECATION")
        val handlers = packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        handlers.isNotEmpty()
    }
}
