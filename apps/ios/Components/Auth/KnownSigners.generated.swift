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

extension NostrSignerDetector {

    /// Ordered list of signers this detector knows about (detection precedence
    /// = array order). Every `urlScheme` here MUST also appear in Info.plist's
    /// `LSApplicationQueriesSchemes`.
    @MainActor
    public static let knownSigners: [NostrSignerInfo] = [
        NostrSignerInfo(kind: .amber, displayName: "Amber"),
        NostrSignerInfo(kind: .primal, displayName: "Primal"),
        NostrSignerInfo(
            kind: .generic(name: "Nostr Connect", scheme: "nostrconnect"),
            displayName: "Nostr Connect"
        ),
    ]
}
