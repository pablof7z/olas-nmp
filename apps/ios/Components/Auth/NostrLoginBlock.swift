import SwiftUI
import UIKit

// MARK: - Info.plist requirement
//
// `UIApplication.shared.canOpenURL` only returns `true` for URL schemes that
// are listed in the *calling* app's Info.plist under `LSApplicationQueriesSchemes`.
// Without that entry the OS returns `false` even when the target app is installed.
//
// Add the following to your app's Info.plist:
//
//   <key>LSApplicationQueriesSchemes</key>
//   <array>
//     <string>nostrsigner</string>   <!-- Amber -->
//     <string>primal</string>        <!-- Primal -->
//     <string>nostrconnect</string>  <!-- generic NIP-46 bridge -->
//   </array>
//
// Re-export the list in NostrSignerDetector.knownSigners if you extend it.

// MARK: - NostrSignerInfo

/// Identity record for one locally-installed Nostr signer app.
public struct NostrSignerInfo: Identifiable, Equatable {

    /// Discriminator for first-class signers. Use `.generic` for any future
    /// signer that does not yet have an explicit case here.
    public enum SignerKind: Equatable {
        /// Amber — the canonical Android NIP-55 signer; also available on iOS
        /// via the `nostrsigner://` URL scheme.
        case amber
        /// Primal — registers the `primal://` scheme and can act as a remote
        /// signer via NIP-46 Nostr Connect.
        case primal
        /// Any other signer reachable via a named scheme.
        case generic(name: String, scheme: String)

        // NOTE: Olas does not currently register a URL scheme that other apps
        // can probe with canOpenURL (as of 2026-05-25). If Olas adds one in
        // the future, add `case olas` here and a matching entry in
        // NostrSignerDetector.knownSigners.

        public var scheme: String {
            switch self {
            case .amber:                    return "nostrsigner"
            case .primal:                   return "primal"
            case .generic(_, let scheme):   return scheme
            }
        }
    }

    public var kind: SignerKind

    /// Human-readable label shown in the signer card (e.g. "Amber").
    public var displayName: String

    /// URL scheme used for detection and deep-linking (e.g. "nostrsigner").
    public var urlScheme: String { kind.scheme }

    /// Stable identifier — uses the URL scheme so it survives reordering.
    public var id: String { urlScheme }

    public init(kind: SignerKind, displayName: String) {
        self.kind = kind
        self.displayName = displayName
    }
}

// MARK: - NostrSignerDetector

/// Probes the device for installed Nostr signer apps by testing URL scheme
/// reachability. Must be called from the main thread (UIApplication requires it).
///
/// Detection precedence matches the order of `knownSigners`: Amber → Primal →
/// nostrconnect-generic. Extend `knownSigners` as new iOS signer apps emerge.
public enum NostrSignerDetector {

    // `knownSigners` is GENERATED from the Rust catalog (nmp_core::signer_catalog)
    // into the sibling file `KnownSigners.generated.swift` as an
    // `extension NostrSignerDetector`. Edit the catalog + re-run
    // `nmp gen signer-catalog`, never this file (#1493 P9).

    /// Returns every known signer whose URL scheme the OS can open.
    /// Safe to call from a `.task { }` modifier — the Task runs on the actor
    /// that owns the view, which is `@MainActor`.
    @MainActor
    public static func detect() -> [NostrSignerInfo] {
        knownSigners.filter { canOpen($0.urlScheme) }
    }

    @MainActor
    private static func canOpen(_ scheme: String) -> Bool {
        guard let url = URL(string: "\(scheme)://") else { return false }
        return UIApplication.shared.canOpenURL(url)
    }
}

// MARK: - NostrLoginBlock

/// Login UI for Nostr apps using NMP.
///
/// Detects installed local signer apps (Amber, Primal, and other NIP-46/NIP-55
/// compatible signers) and surfaces them as one-tap sign-in options. Falls back
/// to a manual key-entry path when no external signers are found.
///
/// ## Usage
/// ```swift
/// NostrLoginBlock(
///     onSignerSelected: { signer in
///         // hand `signer.urlScheme` to your NIP-46 / NIP-55 bridge
///     },
///     onManualKey: {
///         // push your nsec / hex key entry view
///     }
/// )
/// ```
///
/// ## Theming
/// Inject a `NostrContentRenderer` into the environment to override text and
/// accent colors across the whole block:
/// ```swift
/// NostrLoginBlock(...)
///     .nostrContentRenderer(NostrContentRenderer(linkColor: .purple))
/// ```
public struct NostrLoginBlock: View {

    /// Called when the user taps a signer card. The app is responsible for
    /// initiating the NIP-46 / NIP-55 handshake with the chosen signer.
    public var onSignerSelected: (NostrSignerInfo) -> Void

    /// Called when the user taps "Enter your key" (manual nsec / hex path).
    public var onManualKey: () -> Void

    @State private var availableSigners: [NostrSignerInfo] = []

    @Environment(\.nostrContentRenderer) private var renderer

    public init(
        onSignerSelected: @escaping (NostrSignerInfo) -> Void,
        onManualKey: @escaping () -> Void
    ) {
        self.onSignerSelected = onSignerSelected
        self.onManualKey = onManualKey
    }

    public var body: some View {
        VStack(spacing: 16) {
            // Signer cards — one per detected app.
            ForEach(availableSigners) { signer in
                signerCard(for: signer)
            }

            // Manual key entry — always present.
            manualKeyButton

            // Install hint — only shown when no signers are installed.
            if availableSigners.isEmpty {
                installHint
            }
        }
        .task {
            availableSigners = NostrSignerDetector.detect()
        }
    }

    // MARK: - Sub-views

    private func signerCard(for signer: NostrSignerInfo) -> some View {
        Button {
            onSignerSelected(signer)
        } label: {
            HStack(spacing: 14) {
                signerIcon(for: signer)
                    .font(.title2)
                    .foregroundStyle(renderer.linkColor)
                    .frame(width: 32, alignment: .center)

                VStack(alignment: .leading, spacing: 2) {
                    Text(signer.displayName)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(renderer.textColor)
                    Text("Sign in with \(signer.displayName)")
                        .font(.caption)
                        .foregroundStyle(renderer.secondaryTextColor)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(renderer.secondaryTextColor)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(renderer.quoteBackgroundColor, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(renderer.quoteBorderColor, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Sign in with \(signer.displayName)")
    }

    private var manualKeyButton: some View {
        Button {
            onManualKey()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "key.fill")
                    .font(.title2)
                    .foregroundStyle(renderer.secondaryTextColor)
                    .frame(width: 32, alignment: .center)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Enter your key")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(renderer.textColor)
                    Text("Paste your nsec or hex private key")
                        .font(.caption)
                        .foregroundStyle(renderer.secondaryTextColor)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(renderer.secondaryTextColor)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(renderer.quoteBackgroundColor, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(renderer.quoteBorderColor, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Enter your private key manually")
    }

    private var installHint: some View {
        HStack(spacing: 6) {
            Image(systemName: "info.circle")
                .font(.caption)
                .foregroundStyle(renderer.secondaryTextColor)
            Text("Install Amber or Primal for one-tap sign-in")
                .font(.caption)
                .foregroundStyle(renderer.secondaryTextColor)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }

    // MARK: - Helpers

    @ViewBuilder
    private func signerIcon(for signer: NostrSignerInfo) -> some View {
        switch signer.kind {
        case .amber:
            Image(systemName: "person.badge.key.fill")
        case .primal:
            Image(systemName: "bolt.fill")
        case .generic:
            Image(systemName: "arrow.up.forward.app.fill")
        }
    }
}
