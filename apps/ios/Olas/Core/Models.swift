import Foundation

// MARK: - Nostr Event

struct NostrEvent: Codable {
    let id: String
    let author: String  // NMP KernelEvent serializes as "author" not "pubkey"
    let kind: Int
    let content: String
    let tags: [[String]]
    let createdAt: Int64

    enum CodingKeys: String, CodingKey {
        case id, author, kind, content, tags
        case createdAt = "created_at"
    }
}

// MARK: - Photo Post

// Mirrors nmp_nip68::PictureEventRecord JSON exactly.
struct PhotoPost: Identifiable, Codable {
    let id: String           // JSON: "event_id"
    let authorPubkey: String // JSON: "author"
    var authorName: String?
    var authorAvatar: String?
    let images: [ImageMeta]
    let content: String      // JSON: "content" (caption text)
    let hashtags: [String]
    let createdAt: Int64     // JSON: "created_at"
    // Client-only state — not in Rust JSON output, excluded from CodingKeys.
    var reactionCount: Int = 0
    var commentCount: Int = 0
    var zapTotal: Int64 = 0
    var isLiked: Bool = false
    var isBookmarked: Bool = false

    var caption: String { content }

    enum CodingKeys: String, CodingKey {
        case id = "event_id"
        case authorPubkey = "author"
        case authorName, authorAvatar, images, content, hashtags
        case createdAt = "created_at"
    }

    // Rust skips empty arrays (skip_serializing_if = "Vec::is_empty"), so
    // hashtags may be absent. Use decodeIfPresent to avoid keyNotFound errors.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        authorPubkey = try c.decode(String.self, forKey: .authorPubkey)
        authorName = try c.decodeIfPresent(String.self, forKey: .authorName)
        authorAvatar = try c.decodeIfPresent(String.self, forKey: .authorAvatar)
        images = try c.decode([ImageMeta].self, forKey: .images)
        content = try c.decode(String.self, forKey: .content)
        hashtags = try c.decodeIfPresent([String].self, forKey: .hashtags) ?? []
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
    }
}

// Mirrors nmp_nip92_types::MediaDimensions JSON.
struct ImageDimensions: Codable {
    let width: Int
    let height: Int
}

// Mirrors nmp_nip92_types::MediaMeta JSON (aliased as ImageMeta in nmp_nip68).
struct ImageMeta: Codable {
    let url: String
    let sha256: String?
    let mime: String?
    let dimensions: ImageDimensions?
    let blurhash: String?
    let alt: String?
}

// MARK: - Profile

struct OlasProfile: Codable {
    let pubkey: String
    var name: String?
    var displayName: String?
    var about: String?
    var picture: String?
    var banner: String?
    var nip05: String?
    var lud16: String?

    var displayNameOrName: String {
        displayName ?? name ?? String(pubkey.prefix(8))
    }
}

// MARK: - Follow Pack (Rust-decoded from kind:30000 events)

/// Decoded from `olas_decode_follow_pack_event_json` — the Rust FFI source of truth.
/// The old hardcoded `FollowPack.defaults` static has been removed (P0-A fix).
struct FollowPackDescriptor: Identifiable, Codable {
    let id: String           // JSON: "id"  (kind:30000 `d` tag)
    let name: String
    let description: String
    let accentColor: String  // JSON: "accent_color"
    let pubkeys: [String]    // resolved member pubkeys from `p` tags
    let count: Int

    enum CodingKeys: String, CodingKey {
        case id, name, description, count, pubkeys
        case accentColor = "accent_color"
    }
}

/// Returned by `olas_apply_follow_pack_pubkeys` after dispatching all follows.
struct FollowPackApplyResult: Codable {
    let followCount: Int    // JSON: "follow_count"
    let feedDefault: String // JSON: "feed_default" — "following" | "network"

    enum CodingKeys: String, CodingKey {
        case followCount = "follow_count"
        case feedDefault = "feed_default"
    }
}

// MARK: - Feed Mode

enum FeedMode { case following, network }

// MARK: - Notification

struct OlasNotification: Identifiable {
    let id: String
    let type: NotificationType
    let actorPubkey: String
    var actorName: String?
    var actorAvatar: String?
    let postId: String?
    var postThumbnail: String?
    let createdAt: Int64
    var groupCount: Int = 1

    enum NotificationType {
        case reaction, comment, mention, follow, repost, zap(Int64)
    }
}

// MARK: - Upload State

/// Canonical picture-post upload state machine, shared verbatim with Android
/// (`UploadStep` in feature/compose/UploadViewModel.kt). Hashing + signing live
/// in Rust (nmp-blossom hashes; nmp.publish signs), so they are not native
/// steps. The native side only encodes (downsample + JPEG), then reflects the
/// two Rust action lifecycles:
///   idle → encoding → uploading(progress) → publishing → done | error
enum UploadStep: Equatable {
    case idle
    case encoding
    case uploading(Double)
    case publishing
    case done
    case error(String)
}

// MARK: - Grouped Notification (P3-B)

/// Decoded from `olas_group_notifications_json`.
/// Rust groups individual notifications by (kind, target_post_id) and deduplicates actors.
struct OlasGroupedNotification: Identifiable, Codable {
    let groupId: String
    let kind: String           // "reaction" | "comment" | "mention" | "follow" | "repost" | "zap"
    let targetPostId: String?
    let actorPubkeys: [String] // hex pubkeys, deduped, most-recent first
    let count: Int
    let latestTs: Int64        // unix seconds — used for time-section assignment (native)
    let zapSats: Int64?

    var id: String { groupId }
}

// MARK: - Caption Tags (P3-C)

/// Decoded from `olas_parse_caption_tags_json`.
struct CaptionTagsPayload: Codable {
    let content: String
    let pTags: [[String]]  // [["p", hex_pubkey], …]
    let tTags: [[String]]  // [["t", hashtag], …]

    enum CodingKeys: String, CodingKey {
        case content
        case pTags = "p_tags"
        case tTags = "t_tags"
    }
}

// MARK: - Relay

struct RelayEntry: Identifiable {
    let id: String
    var url: String
    var role: String // "both", "read", "write", "indexer"
    var isConnected: Bool = false
    var latencyMs: Int?
}
