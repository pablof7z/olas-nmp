import SwiftUI

// MARK: - Wire types

/// One row of the kernel's `projections.configured_relays` array, paired with
/// the kernel-emitted presentation tokens for its role.
///
/// The kernel emits the canonical role token (`both`, `read`, `write`,
/// `indexer`, `both,indexer`, …) on each relay row, and the human-readable
/// `label` + semantic `tint` token for every role through the
/// `relay_role_options` projection (Rust `relay_role_options()`,
/// ADR-0032 / ADR-0041). The shell looks the `role` up in those options and
/// hands the resulting `roleLabel` + `roleTint` to this row — it never derives
/// either string itself.
public struct NostrRelayEditRow: Codable, Identifiable, Equatable, Sendable, RenderIdentifiable {
    public var id: String { url }
    public let url: String
    public let role: String
    /// Human-readable label for `role`, taken from the kernel's
    /// `relay_role_options` projection (never derived in Swift).
    public let roleLabel: String
    /// Semantic tint token for `role` (`accent`, `info`, `success`,
    /// `neutral`, …), taken from the kernel's `relay_role_options`
    /// projection (never derived in Swift).
    public let roleTint: String

    public init(url: String, role: String, roleLabel: String, roleTint: String) {
        self.url = url
        self.role = role
        self.roleLabel = roleLabel
        self.roleTint = roleTint
    }

    public func rendersIdentically(_ other: Self) -> Bool {
        self.url == other.url
            && self.role == other.role
            && self.roleLabel == other.roleLabel
            && self.roleTint == other.roleTint
    }
}

/// One entry of the kernel's top-level `relay_statuses` snapshot field
/// (i.e. `snapshot.relay_statuses[]`, not nested inside `projections`).
///
/// `connection` is one of `connected | connecting | disconnected |
/// error` (closed token set). Callers typically fold the array into a
/// `[relay_url: connection]` dictionary before handing it to
/// `NostrRelayList`.
public struct NostrRelayConnectionStatus: Codable, Equatable {
    public let relayUrl: String
    public let connection: String
    public let reconnectCount: UInt32

    public init(relayUrl: String, connection: String, reconnectCount: UInt32) {
        self.relayUrl = relayUrl
        self.connection = connection
        self.reconnectCount = reconnectCount
    }

    private enum CodingKeys: String, CodingKey {
        case relayUrl = "relay_url"
        case connection
        case reconnectCount = "reconnect_count"
    }
}

// MARK: - Component

/// Row model for the relay list ForEach, bundling relay + connection status
/// so that EquatableRow sees the full render state when connection status changes.
private struct RelayListRowModel: RenderIdentifiable, Sendable {
    let relay: NostrRelayEditRow
    let connection: String?

    func rendersIdentically(_ other: Self) -> Bool {
        relay.rendersIdentically(other.relay) && connection == other.connection
    }
}

/// Relay list component — shows a user's configured relays with
/// connection-status dots and role badges.
///
/// Mirrors NDK's svelte `RelayList`. Data comes straight from the NMP
/// snapshot: rows from `projections.configured_relays` (with role
/// `label`/`tint` resolved from `relay_role_options`), connection
/// statuses folded from the top-level `relay_statuses` field keyed by
/// relay URL.
public struct NostrRelayList: View {
    public let relays: [NostrRelayEditRow]
    /// Keyed by relay URL — caller merges from `relay_statuses` (typically
    /// `Dictionary(uniqueKeysWithValues: snapshot.relayStatuses.map { ($0.relayUrl, $0.connection) })`).
    public var connectionStatus: [String: String]
    public var onRelayTap: ((NostrRelayEditRow) -> Void)?

    public init(
        relays: [NostrRelayEditRow],
        connectionStatus: [String: String] = [:],
        onRelayTap: ((NostrRelayEditRow) -> Void)? = nil
    ) {
        self.relays = relays
        self.connectionStatus = connectionStatus
        self.onRelayTap = onRelayTap
    }

    public var body: some View {
        if relays.isEmpty {
            VStack {
                Text("No relays configured")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
        } else {
            VStack(spacing: 0) {
                ForEach(relays) { relay in
                    EquatableRow(model: RelayListRowModel(relay: relay, connection: connectionStatus[relay.url])) { m in
                        NostrRelayRow(
                            url: m.relay.url,
                            roleLabel: m.relay.roleLabel,
                            roleTint: m.relay.roleTint,
                            connection: m.connection,
                            onTap: onRelayTap.map { handler in { handler(m.relay) } }
                        )
                    }
                    .equatable()
                }
            }
        }
    }
}

// MARK: - Row primitive

/// The base relay-row primitive: a connection-status dot, a monospaced relay
/// URL, and a role badge.
///
/// Takes the kernel-emitted `roleLabel` and semantic `roleTint` token
/// directly — it performs NO role→label / role→tint derivation. The only
/// presentation logic is `tintColor(for:)`, which maps a semantic tint token
/// to a SwiftUI `Color` (genuine rendering, not business logic).
public struct NostrRelayRow: View {
    public let url: String
    public let roleLabel: String
    public let roleTint: String
    public let connection: String?
    public let onTap: (() -> Void)?

    public init(
        url: String,
        roleLabel: String,
        roleTint: String,
        connection: String? = nil,
        onTap: (() -> Void)? = nil
    ) {
        self.url = url
        self.roleLabel = roleLabel
        self.roleTint = roleTint
        self.connection = connection
        self.onTap = onTap
    }

    public var body: some View {
        HStack(spacing: 10) {
            ConnectionDot(status: connection)

            Text(displayUrl)
                .font(.body.monospaced())
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)

            RoleBadge(
                label: roleLabel,
                tint: NostrRelayRow.tintColor(for: roleTint)
            )
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(displayUrl), \(roleLabel), \(accessibilityStatus)")
        .accessibilityAddTraits(onTap != nil ? .isButton : [])
    }

    /// Resolve a relay-role tint token (or fallback hex) into a `Color`.
    ///
    /// The kernel currently emits semantic tokens (`accent`, `info`,
    /// `success`, `neutral`) — those are checked first. A 6-char hex
    /// string is also accepted via `color(hex:)` to stay
    /// forward-compatible. Anything unrecognised falls back to
    /// `.secondary`.
    ///
    /// This is the ONLY presentation logic in the relay row: a token→Color
    /// mapping (genuine rendering). Label and tint *selection* live in the
    /// kernel's `relay_role_options` projection.
    public static func tintColor(for token: String) -> Color {
        switch token.lowercased() {
        case "accent": return .accentColor
        case "info": return .blue
        case "success": return .green
        case "warning": return .orange
        case "danger", "error": return .red
        case "neutral": return .secondary
        default:
            return color(hex: token) ?? .secondary
        }
    }

    /// Parse a 6-character RGB hex string (optionally prefixed with `#`).
    /// Returns `nil` if the input is not a valid 6-char hex.
    ///
    /// Defined as a component-scoped helper rather than a `Color` extension so
    /// the vendored copy never collides with a host app's own `Color(hex:)`
    /// (issue #996).
    private static func color(hex: String) -> Color? {
        let cleaned = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard cleaned.count == 6,
              let rgb = UInt64(cleaned, radix: 16) else { return nil }
        return Color(
            red:   Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >>  8) & 0xFF) / 255,
            blue:  Double( rgb        & 0xFF) / 255
        )
    }

    private var displayUrl: String {
        if url.hasPrefix("wss://") {
            return String(url.dropFirst("wss://".count))
        }
        if url.hasPrefix("ws://") {
            return String(url.dropFirst("ws://".count))
        }
        return url
    }

    private var accessibilityStatus: String {
        switch connection {
        case "connected": return "connected"
        case "connecting": return "connecting"
        case "error": return "error"
        case "disconnected": return "disconnected"
        default: return "status unknown"
        }
    }
}

// MARK: - Connection dot

private struct ConnectionDot: View {
    let status: String?

    @State private var pulse: Bool = false

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 8, height: 8)
            .opacity(isConnecting ? (pulse ? 0.4 : 1.0) : 1.0)
            .onAppear {
                guard isConnecting else { return }
                withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            }
            .accessibilityHidden(true)
    }

    private var isConnecting: Bool { status == "connecting" }

    private var color: Color {
        switch status {
        case "connected": return .green
        case "connecting": return .orange
        case "error": return .red
        default: return .secondary
        }
    }
}

// MARK: - Role badge

private struct RoleBadge: View {
    let label: String
    let tint: Color

    var body: some View {
        Text(label)
            .font(.caption.weight(.medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(tint, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
    }
}
