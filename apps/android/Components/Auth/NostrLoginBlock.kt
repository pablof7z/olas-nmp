package org.nmp.registry

// MARK: - AndroidManifest requirement
//
// `PackageManager.queryIntentActivities` on the `nostrsigner:` scheme only
// returns results for packages that are listed in the *calling* app's manifest
// under `<queries>`. Without this entry Android 11+ (API 30+) returns an
// empty list even when Amber is installed.
//
// Add the following to your app's AndroidManifest.xml inside <manifest>:
//
//   <queries>
//       <intent>
//           <action android:name="android.intent.action.VIEW" />
//           <data android:scheme="nostrsigner" />
//       </intent>
//   </queries>
//
// Extend KNOWN_NOSTR_SIGNERS in ExternalSignerCapabilityBridge.kt if you
// add support for future signer apps.

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// ── SignerState wire ──────────────────────────────────────────────────────────

/**
 * Minimal ADR-0048 D6 signer-state projection consumed by [NostrLoginBlock].
 *
 * The full [org.nmp.android.model.SignerState] shape is the authoritative
 * version in the Chirp app; this is a gallery-local mirror to keep the
 * login-block self-contained (the gallery does not depend on the Chirp app
 * module). Both shapes must match the Rust `SignerStateDto` wire.
 */
@kotlinx.serialization.Serializable
data class LoginBlockSignerState(
    @kotlinx.serialization.SerialName("signer_kind") val signerKind: String = "",
    val state: String = "",
    val reason: String? = null,
    @kotlinx.serialization.SerialName("is_ready") val isReady: Boolean = false,
    @kotlinx.serialization.SerialName("is_awaiting_approval") val isAwaitingApproval: Boolean = false,
    @kotlinx.serialization.SerialName("is_reconnecting") val isReconnecting: Boolean = false,
    @kotlinx.serialization.SerialName("is_unavailable") val isUnavailable: Boolean = false,
    @kotlinx.serialization.SerialName("is_failed") val isFailed: Boolean = false,
)

// ── Presentation state (pure — unit-testable without Compose) ────────────────

/** Visual tone of a signer card. Derived ONLY from the pre-computed `is*`
 *  flags (ADR-0032 D6 — the shell never re-derives state from strings). */
enum class SignerCardTone { Default, InProgress, Ready, Degraded }

/**
 * Pure presentation state for one signer card. [SignerCard] renders exactly
 * this; the unit tests assert on exactly this — one source of truth for the
 * render rule, no test-side mirrors.
 */
data class SignerCardUi(
    val tone: SignerCardTone,
    /** Status line under the signer name; null = default "Sign in with …". */
    val statusLabel: String?,
    /** Replace the trailing chevron with a spinner. */
    val showSpinner: Boolean,
)

/** Map a signer-state projection value onto card presentation. */
internal fun signerCardUi(state: LoginBlockSignerState?): SignerCardUi {
    val isInProgress = state?.isAwaitingApproval == true || state?.isReconnecting == true
    val isDegraded = state?.isFailed == true || state?.isUnavailable == true
    val isReady = state?.isReady == true
    val tone = when {
        isDegraded -> SignerCardTone.Degraded
        isInProgress -> SignerCardTone.InProgress
        isReady -> SignerCardTone.Ready
        else -> SignerCardTone.Default
    }
    val statusLabel = when {
        state?.isUnavailable == true -> "Signer unavailable"
        state?.isFailed == true -> "Connection failed"
        state?.isAwaitingApproval == true -> "Waiting for approval…"
        state?.isReconnecting == true -> "Reconnecting…"
        isReady -> "Connected"
        else -> null
    }
    return SignerCardUi(tone = tone, statusLabel = statusLabel, showSpinner = isInProgress)
}

// ── NostrLoginBlock ───────────────────────────────────────────────────────────

/**
 * Login UI for Nostr apps using NMP.
 *
 * Detects installed local signer apps (currently Amber / `nostrsigner:` via
 * Android `PackageManager`) and surfaces each as a one-tap sign-in option.
 * Falls back to a manual key-entry path when no external signers are found —
 * matching the SwiftUI `NostrLoginBlock` at parity.
 *
 * ## NIP-55 flow
 *
 * When the user taps "Sign in with Amber":
 * 1. `onSignerSelected` fires with the chosen [NostrSignerInfo].
 * 2. The app should initiate the ADR-0048 `get_public_key` capability
 *    request via the [ExternalSignerCapabilityBridge].
 * 3. The `signerState` projection drives the `isAwaitingApproval` /
 *    `isReady` / `isUnavailable` / `isFailed` states shown inline.
 *
 * ## Testability (Stage 4)
 *
 * Every interactive element carries a `testTag` so the adb-driven
 * emulator E2E can locate and tap it without UI automation.
 *
 * ## Usage
 *
 * ```kotlin
 * NostrLoginBlock(
 *     onSignerSelected = { signer ->
 *         // call ExternalSignerCapabilityBridge to initiate get_public_key
 *     },
 *     onManualKey = {
 *         // navigate to your nsec / hex key entry view
 *     },
 * )
 * ```
 *
 * To show the in-progress / ready / error state while a sign-in is pending,
 * pass the current `signerState`:
 *
 * ```kotlin
 * NostrLoginBlock(
 *     onSignerSelected = { ... },
 *     onManualKey = { ... },
 *     signerState = model.signerState.collectAsStateWithLifecycle().value?.toLoginBlockState(),
 * )
 * ```
 *
 * @param onSignerSelected Called when the user taps a signer card. Provides
 *   the chosen [NostrSignerInfo]; the app initiates the capability flow.
 * @param onManualKey Called when the user taps "Enter your key".
 * @param signerState Optional live signer state driving the progress / health
 *   indicator. Null while no remote-signer session is in progress.
 * @param modifier Modifier applied to the root `Column`.
 */
@Composable
fun NostrLoginBlock(
    onSignerSelected: (NostrSignerInfo) -> Unit,
    onManualKey: () -> Unit,
    signerState: LoginBlockSignerState? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var availableSigners by remember { mutableStateOf<List<NostrSignerInfo>>(emptyList()) }

    // Detection runs once on composition. Uses LaunchedEffect instead of
    // remember { detect() } so the PM probe happens asynchronously after
    // the first frame renders (mirrors the SwiftUI `.task { }` approach).
    LaunchedEffect(Unit) {
        availableSigners = detectInstalledSigners(context.packageManager)
    }

    Column(
        modifier = modifier.testTag("nostr_login_block"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Signer cards — one per detected app.
        availableSigners.forEach { signer ->
            SignerCard(
                signer = signer,
                signerState = signerState?.takeIf { it.signerKind == "nip55" },
                onClick = { onSignerSelected(signer) },
            )
        }

        // Manual key entry — always present.
        ManualKeyButton(onClick = onManualKey)

        // Install hint — only shown when no signers are installed.
        if (availableSigners.isEmpty()) {
            InstallHint()
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SignerCard(
    signer: NostrSignerInfo,
    signerState: LoginBlockSignerState?,
    onClick: () -> Unit,
) {
    // ONE render rule — the same pure function the unit tests assert on.
    val ui = signerCardUi(signerState)

    val borderColor: Color = when (ui.tone) {
        SignerCardTone.Degraded -> MaterialTheme.colorScheme.error
        SignerCardTone.InProgress -> Color(0xFFF59E0B) // amber-400
        SignerCardTone.Ready -> Color(0xFF22C55E) // green-500
        SignerCardTone.Default -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("signer_card_${signer.displayName.lowercase()}")
            .semantics {
                contentDescription = "Sign in with ${signer.displayName}"
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Leading icon
            Icon(
                imageVector = signerIcon(signer),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            // Text column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = signer.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = ui.statusLabel ?: "Sign in with ${signer.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (ui.tone) {
                        SignerCardTone.Degraded -> MaterialTheme.colorScheme.error
                        SignerCardTone.InProgress -> Color(0xFFF59E0B)
                        SignerCardTone.Ready -> Color(0xFF22C55E)
                        SignerCardTone.Default -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Trailing: spinner when pending, chevron otherwise.
            if (ui.showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color(0xFFF59E0B),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ManualKeyButton(onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_key_button")
            .semantics { contentDescription = "Enter your private key manually" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Enter your key",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = "Paste your nsec or hex private key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstallHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Install Amber or Primal for one-tap sign-in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Icon helpers ──────────────────────────────────────────────────────────────

private fun signerIcon(signer: NostrSignerInfo): ImageVector = when (signer.intentScheme) {
    "nostrsigner" -> Icons.Default.Person // Amber
    "primal" -> Icons.Default.Key         // Primal (bolt icon not in default set)
    else -> Icons.Default.Key
}
