package org.nmp.registry

// ─────────────────────────────────────────────────────────────────────────────
// THIS FILE IS GENERATED. DO NOT EDIT BY HAND.
//
// Source of truth: nmp_core::signer_catalog (crates/nmp-core/src/signer_catalog.rs).
// Regenerate via:
//   cargo run -q -p nmp-core --bin dump_signer_catalog \
//     | cargo run -q -p nmp-codegen -- gen signer-catalog
//
// The CI gate (.github/workflows/codegen-drift.yml, `nmp gen signer-catalog
// --check`) fails any PR whose generated native signer lists differ from a fresh
// run, so they can never drift from the Rust catalog.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Ordered list of signers the Android detector knows about (detection
 * precedence = list order). Consumed by `detectInstalledSigners` in
 * ExternalSignerWire.kt (same package). Every `intentScheme` here MUST also
 * appear in `<queries>` in AndroidManifest.xml.
 */
val KNOWN_NOSTR_SIGNERS: List<NostrSignerInfo> = listOf(
    NostrSignerInfo(
        displayName = "Amber",
        intentScheme = "nostrsigner",
        contentAuthority = "com.greenart7c3.nostrsigner",
        packageName = "com.greenart7c3.nostrsigner",
        installHint = "Install Amber for one-tap sign-in",
    ),
    NostrSignerInfo(
        displayName = "Primal",
        intentScheme = "primal",
        contentAuthority = null,
        packageName = "net.primal.android",
        installHint = "Install Primal for one-tap sign-in",
    ),
)
