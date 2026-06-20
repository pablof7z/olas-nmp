import Foundation

/// Wire type for a Nostr user profile, decoded from the `nmp-profile`
/// projection emitted by the kernel.
///
/// `npub` and `npubShort` are always Rust-formatted — never reformat
/// them in Swift (aim.md §6.9).
public struct ProfileWire: Codable, Equatable, Sendable {
    public let pubkey: String
    public let displayName: String?
    public let about: String?
    public let pictureUrl: String?
    public let nip05: String?
    /// Full bech32 `npub1…` string. Use for copy / share.
    public let npub: String
    /// Rust-truncated npub (e.g. `npub1abcd…wxyz`). Display only.
    public let npubShort: String

    public init(
        pubkey: String,
        displayName: String? = nil,
        about: String? = nil,
        pictureUrl: String? = nil,
        nip05: String? = nil,
        npub: String,
        npubShort: String
    ) {
        self.pubkey = pubkey
        self.displayName = displayName
        self.about = about
        self.pictureUrl = pictureUrl
        self.nip05 = nip05
        self.npub = npub
        self.npubShort = npubShort
    }

    /// Stable display label: `displayName` if set, else `npubShort`.
    public var display: String {
        if let name = displayName, !name.isEmpty { return name }
        return npubShort
    }

    /// Parsed avatar URL; `nil` when no picture is set or URL is empty.
    public var avatarURL: URL? {
        guard let str = pictureUrl, !str.isEmpty else { return nil }
        return URL(string: str)
    }
}
