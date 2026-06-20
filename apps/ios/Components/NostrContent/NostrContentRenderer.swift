import SwiftUI
import UIKit

public struct NostrContentCallbacks: @unchecked Sendable {
    public var onMentionTap: (String) -> Void
    public var onHashtagTap: (String) -> Void
    public var onLinkTap: (URL) -> Void
    public var onImageTap: (URL) -> Void
    public var onEventRefTap: (String) -> Void

    public init(
        onMentionTap: @escaping (String) -> Void = { _ in },
        onHashtagTap: @escaping (String) -> Void = { _ in },
        onLinkTap: @escaping (URL) -> Void = { _ in },
        onImageTap: ((URL) -> Void)? = nil,
        onEventRefTap: @escaping (String) -> Void = { _ in }
    ) {
        self.onMentionTap = onMentionTap
        self.onHashtagTap = onHashtagTap
        self.onLinkTap = onLinkTap
        // `onImageTap` defaults to `onLinkTap` so apps that only wire the
        // generic link handler still get image-tap routing for free.
        self.onImageTap = onImageTap ?? onLinkTap
        self.onEventRefTap = onEventRefTap
    }
}

/// Shared image cache for the default `NostrContentRenderer.imageLoader`.
///
/// `AsyncImage` does not accept a `URLSession`, so its default behaviour
/// relies on whatever is configured on `URLCache.shared`. The default loader
/// in `NostrContentRenderer` therefore drives a dedicated `URLSession`
/// directly (downloading bytes and rendering via `Image(uiImage:)`) so the
/// 32 MB memory / 256 MB disk cache is actually used.
///
/// Apps wanting Kingfisher / Nuke / SDWebImage should replace
/// `NostrContentRenderer.imageLoader` rather than mutate this cache.
public enum NostrImageCache {
    public static let urlCache: URLCache = URLCache(
        memoryCapacity: 32 * 1024 * 1024,
        diskCapacity: 256 * 1024 * 1024,
        diskPath: "nmp-content-image-cache"
    )

    public static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.urlCache = urlCache
        config.requestCachePolicy = .returnCacheDataElseLoad
        return URLSession(configuration: config)
    }()
}

public struct NostrContentRenderer: @unchecked Sendable {
    public var textColor: Color
    public var secondaryTextColor: Color
    public var mentionColor: Color
    public var hashtagColor: Color
    public var linkColor: Color
    public var quoteBorderColor: Color
    public var quoteBackgroundColor: Color
    public var codeBackgroundColor: Color
    public var placeholderColor: Color
    public var callbacks: NostrContentCallbacks
    /// Image loader closure. Apps can swap this for a Kingfisher / Nuke /
    /// SDWebImage adapter without re-deriving the component surface. The
    /// default driver uses a dedicated `URLSession` over `NostrImageCache`
    /// (32 MB memory / 256 MB disk) so disk caching works out of the box.
    public var imageLoader: @Sendable (URL) -> AnyView
    /// Pre-resolved emoji shortcode → image map. Apps populate this from
    /// kind:0 / NIP-30 emoji tag data; an empty dict (the default) makes the
    /// content view fall back to literal `:shortcode:` text.
    public var emojiImages: [String: UIImage]

    public init(
        textColor: Color = .primary,
        secondaryTextColor: Color = .secondary,
        mentionColor: Color = .accentColor,
        hashtagColor: Color = .accentColor,
        linkColor: Color = .accentColor,
        quoteBorderColor: Color = Color.gray.opacity(0.35),
        quoteBackgroundColor: Color = Color.gray.opacity(0.08),
        codeBackgroundColor: Color = Color.gray.opacity(0.15),
        placeholderColor: Color = Color.gray.opacity(0.6),
        callbacks: NostrContentCallbacks = NostrContentCallbacks(),
        imageLoader: (@Sendable (URL) -> AnyView)? = nil,
        emojiImages: [String: UIImage] = [:]
    ) {
        self.textColor = textColor
        self.secondaryTextColor = secondaryTextColor
        self.mentionColor = mentionColor
        self.hashtagColor = hashtagColor
        self.linkColor = linkColor
        self.quoteBorderColor = quoteBorderColor
        self.quoteBackgroundColor = quoteBackgroundColor
        self.codeBackgroundColor = codeBackgroundColor
        self.placeholderColor = placeholderColor
        self.callbacks = callbacks
        self.imageLoader = imageLoader ?? Self.defaultImageLoader
        self.emojiImages = emojiImages
    }

    /// Default loader: drives `NostrImageCache.session` directly (so disk
    /// caching is real, not just memory). Shows `ProgressView()` while
    /// loading, a gray placeholder on failure, `scaledToFill()` on success.
    @Sendable
    public static func defaultImageLoader(_ url: URL) -> AnyView {
        AnyView(NostrDefaultImageView(url: url))
    }
}

/// Default loader view backing `NostrContentRenderer.defaultImageLoader`.
/// Pulled out as a concrete `View` so the loader closure stays
/// `@Sendable`-clean and the cache-driving network call is testable.
private struct NostrDefaultImageView: View {
    let url: URL
    @State private var image: UIImage?
    @State private var didFail: Bool = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else if didFail {
                Color.gray.opacity(0.15)
            } else {
                ZStack {
                    Color.gray.opacity(0.08)
                    ProgressView()
                }
            }
        }
        .task(id: url) {
            await load()
        }
    }

    private func load() async {
        // Reset state when the URL changes.
        if image != nil || didFail {
            image = nil
            didFail = false
        }
        do {
            let (data, _) = try await NostrImageCache.session.data(from: url)
            if let decoded = UIImage(data: data) {
                image = decoded
            } else {
                didFail = true
            }
        } catch {
            didFail = true
        }
    }
}

/// View that resolves a URL through `NostrContentRenderer.imageLoader` so
/// components don't reference `AsyncImage` directly. Apps that install a
/// Kingfisher / Nuke loader on the renderer get every avatar / thumbnail /
/// media grid cell routed through it.
public struct NostrImageView: View {
    public var url: URL
    @Environment(\.nostrContentRenderer) private var renderer

    public init(url: URL) {
        self.url = url
    }

    public var body: some View {
        renderer.imageLoader(url)
    }
}

private struct NostrContentRendererKey: EnvironmentKey {
    static let defaultValue = NostrContentRenderer()
}

public extension EnvironmentValues {
    var nostrContentRenderer: NostrContentRenderer {
        get { self[NostrContentRendererKey.self] }
        set { self[NostrContentRendererKey.self] = newValue }
    }
}

public extension View {
    func nostrContentRenderer(_ renderer: NostrContentRenderer) -> some View {
        environment(\.nostrContentRenderer, renderer)
    }
}
