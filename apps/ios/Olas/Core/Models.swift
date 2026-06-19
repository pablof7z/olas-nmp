import Foundation

// MARK: - Photo Post

struct PhotoPost: Identifiable, Codable {
    let id: String
    let authorPubkey: String
    var authorName: String?
    var authorAvatar: String?
    let images: [ImageMeta]
    let caption: String
    let hashtags: [String]
    var reactionCount: Int = 0
    var commentCount: Int = 0
    var zapTotal: Int64 = 0
    let createdAt: Int64
    var isLiked: Bool = false
    var isBookmarked: Bool = false
}

struct ImageMeta: Codable {
    let url: String
    let sha256: String
    let mime: String
    let width: Int?
    let height: Int?
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

// MARK: - Follow Pack

struct FollowPack: Identifiable {
    let id: String
    let name: String
    let description: String
    let category: String
    let accentColor: String
    let count: Int
    let previewPubkeys: [String]

    static let defaults: [FollowPack] = [
        FollowPack(
            id: "visual-storytellers",
            name: "Visual Storytellers",
            description: "Photographers pushing the boundaries of everyday imagery",
            category: "Photography",
            accentColor: "#0A84FF",
            count: 847,
            previewPubkeys: []
        ),
        FollowPack(
            id: "world-travelers",
            name: "World Travelers",
            description: "Explore the globe through stunning travel photography",
            category: "Travel",
            accentColor: "#34C759",
            count: 1203,
            previewPubkeys: []
        ),
        FollowPack(
            id: "digital-artists",
            name: "Digital Artists",
            description: "Cutting-edge digital art and creative visual design",
            category: "Art",
            accentColor: "#FF375F",
            count: 629,
            previewPubkeys: []
        ),
        FollowPack(
            id: "food-culture",
            name: "Food & Culture",
            description: "Culinary photography celebrating food around the world",
            category: "Food",
            accentColor: "#FBB131",
            count: 412,
            previewPubkeys: []
        ),
        FollowPack(
            id: "open-web-builders",
            name: "Open Web Builders",
            description: "Developers and creators building the open social web",
            category: "Tech",
            accentColor: "#BF5AF2",
            count: 2891,
            previewPubkeys: []
        )
    ]
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
        case reaction, comment, mention, follow, repost, zap(Int64?)
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

// MARK: - Relay

struct RelayEntry: Identifiable {
    let id: String
    var url: String
    var role: String // "read-write", "read", "write"
    var isConnected: Bool = false
    var latencyMs: Int?
}
