package org.nmp.registry

import android.content.Intent
import android.net.Uri

// ── Amber / NIP-55 encoding + decoding ────────────────────────────────────────
//
// Everything in this file is the mechanical translation between the
// nmp-signer-iface wire shapes (ExternalSignerWire.kt) and Amber's NIP-55
// Intent contract. Pure top-level functions where possible so unit tests
// exercise the SAME logic the bridge executes (no test-side copies).
//
// Vendored with ExternalSignerCapabilityBridge.kt + ExternalSignerWire.kt as
// one unit (ADR-0048 Stage 2): byte-identical copies except the package line,
// see VendorDriftGateTest.

/**
 * THE transport-selection rule (ADR-0048 D2) — a mechanical consequence of
 * fields Rust set on the request, never host policy (D7):
 *
 * | Condition | Mechanism |
 * |---|---|
 * | `forceInteractive == true` | Intent |
 * | `signerPackage` known AND method's permission in `grantedPermissions` | ContentResolver |
 * | otherwise | Intent |
 */
internal fun shouldUseContentResolver(request: ExternalSignerRequest): Boolean =
    !request.forceInteractive &&
        request.signerPackage != null &&
        request.grantedPermissions.any { p -> p.kind.startsWith(request.method.toPermissionKind()) }

/**
 * Select the reply value from an Amber `RESULT_OK` Intent (Stage-4 emulator
 * finding #3).
 *
 * Amber's `IntentUtils.sendResult` sets:
 *  - `result` extra — signature hex for `sign_event`; pubkey for
 *    `get_public_key`; ciphertext/plaintext for encrypt/decrypt.
 *  - `event` extra — the full signed-event JSON (`sign_event` replies).
 *
 * Rust's `parse_signed_event_response` verifies id + schnorr signature on the
 * COMPLETE event, so `sign_event` must return the `event` extra; everything
 * else uses `result`. Mechanical extra selection, not policy (D7).
 */
internal fun selectAmberResultValue(
    method: String,
    eventExtra: String?,
    resultExtra: String?,
): String? = if (method == "sign_event") {
    eventExtra.takeUnless { it.isNullOrBlank() } ?: resultExtra
} else {
    resultExtra
}

/**
 * Build the Amber-specific permissions JSON array from a `Nip55Permission` list.
 *
 * Our internal format: `kind` is a combined string like `"sign_event:1"`,
 * `"nip44_encrypt"`, etc.
 *
 * Amber expects: `[{"type":"sign_event","kind":1},{"type":"nip44_encrypt"}]`
 * — a separate `type` (method) and optional integer `kind` (event kind).
 */
internal fun buildAmberPermissionsJsonInternal(permissions: List<Nip55Permission>): String {
    val sb = StringBuilder("[")
    permissions.forEachIndexed { idx, perm ->
        if (idx > 0) sb.append(",")
        val combined = perm.kind
        val colonIdx = combined.indexOf(':')
        if (colonIdx >= 0) {
            // "sign_event:1" → type="sign_event", kind=1
            val typePart = combined.substring(0, colonIdx)
            val kindPart = combined.substring(colonIdx + 1).toIntOrNull()
            if (kindPart != null) {
                sb.append("""{"type":"$typePart","kind":$kindPart}""")
            } else {
                sb.append("""{"type":"$typePart"}""")
            }
        } else {
            // "nip44_encrypt" → type="nip44_encrypt"
            sb.append("""{"type":"$combined"}""")
        }
    }
    sb.append("]")
    return sb.toString()
}

/**
 * Build the NIP-55 Intent for one `ExternalSignerRequest`.
 *
 * Amber (v6.x) reads the method PAYLOAD from the Intent data URI
 * (`nostrsigner:<url-encoded payload>`) and every other parameter from
 * Intent extras. Amber's SignerActivity branches on the presence of
 * `Browser.EXTRA_APPLICATION_ID` to choose between URI-query parsing and
 * extras parsing. We do NOT set that extra, so extras-based parsing is
 * always used.
 *
 * Amber requires:
 *   intent.data             = nostrsigner:<Uri.encode(payload)>
 *                             (unsigned-event JSON / ciphertext / …;
 *                             bare `nostrsigner:` when payload empty)
 *   extras["type"]          = method tag string (mandatory)
 *   extras["id"]            = caller request id (echoed in the reply)
 *   extras["returnType"]    = "signature" | "event" (default: signature)
 *   extras["current_user"]  = current user pubkey hex (if known)
 *   extras["pubkey"]        = counterparty pubkey hex (for encrypt/decrypt)
 *   extras["permissions"]   = JSON array string in Amber format (first call only)
 *
 * Stage-4 fixes (each found by a failing emulator round):
 *  1. `type` must be an extra, not a URI query param — Amber's extras-path
 *     saw no `type` key → SignerType.INVALID → "malformed nostrsigner
 *     request" (get_public_key round).
 *  2. The payload must ride the data URI, not a `payload` extra — Amber's
 *     `IntentUtils.getIntentDataFromIntent` reads
 *     `intent.data.toString().replace("nostrsigner:", "")` and never looks
 *     at a `payload` extra; an empty URI made `getUnsignedEvent("")` throw
 *     → the same "malformed" dialog (sign_event round). `Uri.encode` is
 *     mandatory: Amber URL-decodes only when its `isUrlEncoded` regex
 *     matches, and encoding also keeps JSON `?`/`#` characters from being
 *     parsed as URI structure.
 */
internal fun buildAmberSignerIntent(request: ExternalSignerRequest): Intent {
    val methodTag = request.method.toNostrSignerMethod()
    val uriPayload = if (request.payload.isNotEmpty()) Uri.encode(request.payload) else ""
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$uriPayload"))
    intent.putExtra("type", methodTag)
    intent.putExtra("id", request.correlationId)
    intent.putExtra("returnType", "signature")
    if (request.currentUser != null) {
        intent.putExtra("current_user", request.currentUser)
    }
    if (request.counterparty != null) {
        intent.putExtra("pubkey", request.counterparty)
    }
    if (request.permissions.isNotEmpty()) {
        // Amber expects `[{"type":"sign_event","kind":1},{"type":"nip44_encrypt"}]`.
        // Our `Nip55Permission.kind` is a combined string ("sign_event:1",
        // "nip44_encrypt", etc.) — expand it to the Amber shape.
        intent.putExtra("permissions", buildAmberPermissionsJsonInternal(request.permissions))
    }

    // Include the package hint so Amber auto-routes when multiple
    // nostrsigner-scheme handlers are installed.
    request.signerPackage?.let { pkg -> intent.setPackage(pkg) }
    // testTag for Stage-4 emulator E2E: the correlation_id is passed
    // as an extra so the adb-driven fake can echo it in RESULT_OK.
    intent.putExtra("nmp_correlation_id", request.correlationId)
    return intent
}

// ── Method mapping helpers ────────────────────────────────────────────────────

/**
 * Map the Rust `ExternalSignerMethod` snake_case tag to the NIP-55
 * `nostrsigner:` method name (used in both the Intent URI and as the
 * ContentProvider suffix).
 *
 * Amber uses: `get_public_key`, `sign_event`, `nip44_encrypt`,
 * `nip44_decrypt`, `nip04_encrypt`, `nip04_decrypt`.
 */
internal fun String.toNostrSignerMethod(): String = when (this) {
    "get_public_key" -> "get_public_key"
    "sign_event" -> "sign_event"
    "nip44_encrypt" -> "nip44_encrypt"
    "nip44_decrypt" -> "nip44_decrypt"
    "nip04_encrypt" -> "nip04_encrypt"
    "nip04_decrypt" -> "nip04_decrypt"
    else -> this
}

/**
 * Map a method tag to its corresponding NIP-55 permission kind string
 * used in the permission batch.
 *
 * E.g. `"sign_event"` → `"sign_event:"` (prefix for "sign_event:1" etc.),
 * `"nip44_encrypt"` → `"nip44_encrypt"`.
 */
internal fun String.toPermissionKind(): String = when (this) {
    "sign_event" -> "sign_event:"
    "nip44_encrypt" -> "nip44_encrypt"
    "nip44_decrypt" -> "nip44_decrypt"
    "nip04_encrypt" -> "nip04_encrypt"
    "nip04_decrypt" -> "nip04_decrypt"
    else -> this
}
