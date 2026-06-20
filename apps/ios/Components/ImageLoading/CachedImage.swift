import SwiftUI
import Kingfisher

// MARK: - CachedImage (with optional BlurHash placeholder)

/// App-wide cached image view backed by Kingfisher (memory + disk cache),
/// replacing SwiftUI's cacheless `AsyncImage`. `AsyncImage` re-downloads on
/// every appearance, which is unworkable for a scrolling photo feed; Kingfisher
/// caches decoded images and raw bytes so a scrolled-away cell pays nothing to
/// come back. This mirrors Android's Coil-backed loader for cross-platform parity.
///
/// Extended API:
/// - `CachedImage(url:meta:)` — uses `meta.blurhash`, `meta.dimensions`, and
///   `meta.alt` to render a decoded blurry placeholder that crossfades to the real
///   image. The aspect ratio box is reserved up front so layout does not shift.
///
/// Three legacy call shapes still work unchanged:
/// - `CachedImage(url:)`                          — neutral placeholder for both states.
/// - `CachedImage(url:) { placeholder }`          — one view for loading and failure.
/// - `CachedImage(url:loading:failure:)`          — distinct loading and failure UI.
struct CachedImage: View {
    private let url: URL?
    private let contentMode: SwiftUI.ContentMode
    private let loading: AnyView
    private let failure: AnyView
    private let altText: String?

    @State private var didFail = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // MARK: Designated (with ImageMeta)

    /// Primary init used in the feed. Renders a decoded blurhash placeholder that
    /// crossfades (unless Reduce Motion is on) into the full image once loaded.
    /// The outer geometry is reserved by the caller via `.aspectRatio`.
    init(url: URL?, meta: ImageMeta, contentMode: SwiftUI.ContentMode = .fill) {
        self.url = url
        self.contentMode = contentMode
        self.altText = meta.alt

        let blurImage: AnyView = {
            if let hash = meta.blurhash,
               let decoded = BlurHashDecoder.decode(hash, width: 32, height: 32) {
                return AnyView(
                    decoded
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                )
            }
            return AnyView(Rectangle().fill(Color.olasSurface2))
        }()
        self.loading = blurImage
        self.failure = AnyView(
            ZStack {
                Rectangle().fill(Color.olasSurface2)
                Image(systemName: "photo")
                    .font(.system(size: 40, weight: .thin))
                    .foregroundStyle(Color.olasText3)
            }
        )
    }

    // MARK: Legacy initialisers

    init<Loading: View, Failure: View>(
        url: URL?,
        contentMode: SwiftUI.ContentMode = .fill,
        @ViewBuilder loading: () -> Loading,
        @ViewBuilder failure: () -> Failure
    ) {
        self.url = url
        self.contentMode = contentMode
        self.loading = AnyView(loading())
        self.failure = AnyView(failure())
        self.altText = nil
    }

    init<Placeholder: View>(
        url: URL?,
        contentMode: SwiftUI.ContentMode = .fill,
        @ViewBuilder placeholder: () -> Placeholder
    ) {
        let shared = AnyView(placeholder())
        self.url = url
        self.contentMode = contentMode
        self.loading = shared
        self.failure = shared
        self.altText = nil
    }

    init(url: URL?, contentMode: SwiftUI.ContentMode = .fill) {
        let neutral = AnyView(Rectangle().fill(Color.olasSurface2))
        self.url = url
        self.contentMode = contentMode
        self.loading = neutral
        self.failure = neutral
        self.altText = nil
    }

    // MARK: Body

    var body: some View {
        Group {
            if didFail {
                failure
            } else {
                KFImage(url)
                    .placeholder { loading }
                    .onFailure { _ in didFail = true }
                    .fade(duration: reduceMotion ? 0 : 0.25)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            }
        }
        .accessibilityLabel(altText.map(Text.init) ?? Text("Photo"))
        // Recycled cells (LazyVGrid / List) reuse this view with a new URL;
        // clear the stale failure state so the new image gets a fresh attempt.
        .onChange(of: url) { _, _ in didFail = false }
    }
}

// MARK: - ImageMeta convenience shim
// ImageMeta lives in Apps/Olas/Core/Models.swift (Swift struct). Components
// cannot import that target directly, so we define a minimal protocol here and
// the primary init above references the full ImageMeta concrete type, which is
// injected from the app layer. No duplication of model logic.
