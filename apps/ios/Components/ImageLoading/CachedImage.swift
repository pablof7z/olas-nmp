import SwiftUI
import Kingfisher

/// App-wide cached image view backed by Kingfisher (memory + disk cache),
/// replacing SwiftUI's cacheless `AsyncImage`. `AsyncImage` re-downloads on
/// every appearance, which is unworkable for a scrolling photo feed; Kingfisher
/// caches decoded images and raw bytes so a scrolled-away cell pays nothing to
/// come back. This mirrors Android's Coil-backed loader for cross-platform parity.
///
/// Three call shapes:
/// - `CachedImage(url:)`                          — neutral placeholder for both states.
/// - `CachedImage(url:) { placeholder }`          — one view for loading and failure.
/// - `CachedImage(url:loading:failure:)`          — distinct loading and failure UI.
struct CachedImage: View {
    private let url: URL?
    private let contentMode: SwiftUI.ContentMode
    private let loading: AnyView
    private let failure: AnyView

    @State private var didFail = false

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
    }

    init(url: URL?, contentMode: SwiftUI.ContentMode = .fill) {
        let neutral = AnyView(Rectangle().fill(Color.olasSurface2))
        self.url = url
        self.contentMode = contentMode
        self.loading = neutral
        self.failure = neutral
    }

    var body: some View {
        Group {
            if didFail {
                failure
            } else {
                KFImage(url)
                    .placeholder { loading }
                    .onFailure { _ in didFail = true }
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            }
        }
        // Recycled cells (LazyVGrid / List) reuse this view with a new URL;
        // clear the stale failure state so the new image gets a fresh attempt.
        .onChange(of: url) { _, _ in didFail = false }
    }
}
