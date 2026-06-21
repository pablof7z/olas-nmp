import SwiftUI

// MARK: - Active zoom id environment key

/// The `id` of the item currently displayed in the fullscreen lift overlay,
/// or nil when the overlay is closed. Source thumbnails read this to yield
/// geometry ownership to the fullscreen image while the overlay is open.
struct ActiveZoomIdKey: EnvironmentKey {
    static let defaultValue: String? = nil
}

extension EnvironmentValues {
    var activeZoomId: String? {
        get { self[ActiveZoomIdKey.self] }
        set { self[ActiveZoomIdKey.self] = newValue }
    }
}

// MARK: - Zoom transition helpers (used by source AND destination views)

extension View {
    /// Apply matchedGeometryEffect as a SOURCE (thumbnail in feed / grid).
    ///
    /// When `isLiftActive` is true the thumbnail yields geometry ownership to
    /// the fullscreen viewer image (which becomes the source). This is the
    /// standard zoom pattern: only ONE of {thumbnail, fullscreen} is the
    /// source at any given time so SwiftUI animates the frame transfer
    /// correctly in both directions (open → zoom in; dismiss → zoom back).
    ///
    /// No-op when namespace is nil (e.g. Reduce Motion is on).
    @ViewBuilder
    func zoomSource(id: String, namespace: Namespace.ID?, isLiftActive: Bool = false) -> some View {
        if let ns = namespace {
            self.matchedGeometryEffect(id: id, in: ns, isSource: !isLiftActive)
        } else {
            self
        }
    }

    /// Apply matchedGeometryEffect as the DESTINATION (fullscreen viewer image).
    ///
    /// The fullscreen image is the ACTIVE SOURCE (`isSource: true`) while it
    /// exists so it occupies its natural full-screen frame. The thumbnail
    /// (non-source while the lift is open) animates between the thumbnail
    /// slot and the fullscreen frame, driving the zoom-in / zoom-out effect.
    ///
    /// No-op when namespace is nil.
    @ViewBuilder
    func zoomDest(id: String, namespace: Namespace.ID?) -> some View {
        if let ns = namespace {
            self.matchedGeometryEffect(id: id, in: ns, isSource: true)
        } else {
            self
        }
    }
}

// MARK: - FullscreenImageView

struct FullscreenImageView: View {
    let post: PhotoPost
    let initialIndex: Int
    /// Namespace shared with source thumbnails. nil when Reduce Motion is on.
    let namespace: Namespace.ID?
    /// Must match the matchedGeometryEffect id used by the tapped source view.
    let sourceId: String
    /// Called to close; ContentView dismisses the overlay with animation so
    /// matchedGeometryEffect can animate the image back to its source slot.
    let onDismiss: () -> Void

    @State private var dragOffset: CGFloat = 0
    @State private var magnification: CGFloat = 1.0
    @State private var currentIndex: Int

    init(
        post: PhotoPost,
        initialIndex: Int,
        namespace: Namespace.ID?,
        sourceId: String,
        onDismiss: @escaping () -> Void
    ) {
        self.post = post
        self.initialIndex = initialIndex
        self.namespace = namespace
        self.sourceId = sourceId
        self.onDismiss = onDismiss
        _currentIndex = State(initialValue: initialIndex)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if post.images.count == 1 {
                singleImageView(post.images[0])
            } else {
                carouselView
            }

            // Controls overlay
            VStack {
                HStack {
                    Spacer()
                    Button { onDismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(.black.opacity(0.5), in: Circle())
                    }
                    .padding(.trailing, OlasSpacing.md)
                    .padding(.top, OlasSpacing.lg)
                }
                Spacer()

                if post.images.count > 1 {
                    HStack(spacing: 5) {
                        ForEach(0..<post.images.count, id: \.self) { i in
                            Circle()
                                .fill(i == currentIndex ? Color.white : Color.white.opacity(0.4))
                                .frame(width: 6, height: 6)
                        }
                    }
                    .padding(.bottom, 48)
                }
            }
        }
        // Drag-to-dismiss: shift + mild scale shrink toward the finger.
        .scaleEffect(max(0.85, 1.0 - dragOffset / 600))
        .offset(y: dragOffset)
        .opacity(Double(1 - abs(dragOffset) / 300))
        .gesture(dragToDismiss)
    }

    // MARK: - Image views

    private func singleImageView(_ image: ImageMeta) -> some View {
        CachedImage(url: URL(string: image.url), contentMode: .fit) {
            ProgressView().tint(.white)
        }
        .scaleEffect(max(1.0, min(4.0, magnification)))
        .gesture(magnifyGesture)
        // Matched geometry: animates from thumbnail to fullscreen and back.
        .zoomDest(id: sourceId, namespace: namespace)
    }

    @State private var scrollPosition: Int? = nil

    private var carouselView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(Array(post.images.enumerated()), id: \.offset) { idx, image in
                    CachedImage(url: URL(string: image.url), contentMode: .fit) {
                        ProgressView().tint(.white)
                    }
                    .scaleEffect(max(1.0, min(4.0, idx == currentIndex ? magnification : 1.0)))
                    .gesture(magnifyGesture)
                    // Animate the initial (tapped) image; others appear instantly.
                    .zoomDest(
                        id: idx == initialIndex ? sourceId : "",
                        namespace: idx == initialIndex ? namespace : nil
                    )
                    .containerRelativeFrame(.horizontal)
                }
            }
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $scrollPosition)
        .onAppear {
            // Ensure the initially-tapped page is visible so matchedGeometryEffect
            // can animate from the correct source thumbnail.
            if initialIndex > 0 { scrollPosition = initialIndex }
        }
        .onChange(of: scrollPosition) { _, newVal in
            if let v = newVal { currentIndex = v }
        }
    }

    // MARK: - Gestures

    private var magnifyGesture: some Gesture {
        MagnifyGesture()
            .onChanged { value in magnification = value.magnification }
            .onEnded { value in
                withAnimation(.olasStandard) {
                    magnification = max(1.0, min(4.0, value.magnification))
                }
            }
    }

    private var dragToDismiss: some Gesture {
        DragGesture()
            .onChanged { value in
                guard magnification <= 1.01 else { return }
                let t = value.translation.height
                if t > 0 { dragOffset = t }
            }
            .onEnded { value in
                if value.translation.height > 100 {
                    // Reset offset so matched-geometry starts from fullscreen center,
                    // then dismiss with animation → image flies back to source slot.
                    dragOffset = 0
                    withAnimation(.olasStandard) { onDismiss() }
                } else {
                    withAnimation(.olasStandard) { dragOffset = 0 }
                }
            }
    }
}
