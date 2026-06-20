import SwiftUI

/// Photo-style grid layout for an ordered list of image URLs.
///
/// Layout rules:
///   • 1 image  — full-width
///   • 2 images — side-by-side, equal halves
///   • 3 images — one large on the left, two stacked on the right
///   • 4+       — 2×2 grid; last visible cell carries a "+N more" overlay
///
/// Tapping a cell calls `NostrContentCallbacks.onImageTap` from the
/// `NostrContentRenderer` environment. Apps that want different routing can
/// provide a `tapHandler` closure to override the environment callback.
///
/// Depends on `swiftui/content-core`.
public struct NostrMediaGrid: View {
    public var imageUrls: [URL]
    public var aspectRatio: CGFloat
    public var cornerRadius: CGFloat
    public var tapHandler: ((URL) -> Void)?

    @Environment(\.nostrContentRenderer) private var renderer

    public init(
        imageUrls: [URL],
        aspectRatio: CGFloat = 16.0 / 9.0,
        cornerRadius: CGFloat = 10,
        tapHandler: ((URL) -> Void)? = nil
    ) {
        self.imageUrls = imageUrls
        self.aspectRatio = aspectRatio
        self.cornerRadius = cornerRadius
        self.tapHandler = tapHandler
    }

    public var body: some View {
        switch imageUrls.count {
        case 0:
            EmptyView()
        case 1:
            singleLayout(imageUrls[0])
        case 2:
            doubleLayout(left: imageUrls[0], right: imageUrls[1])
        case 3:
            tripleLayout(primary: imageUrls[0], secondary: imageUrls[1], tertiary: imageUrls[2])
        default:
            quadLayout(imageUrls)
        }
    }

    @ViewBuilder
    private func singleLayout(_ url: URL) -> some View {
        cell(for: url, overlay: nil)
            .aspectRatio(aspectRatio, contentMode: .fit)
    }

    @ViewBuilder
    private func doubleLayout(left: URL, right: URL) -> some View {
        HStack(spacing: 2) {
            cell(for: left, overlay: nil)
            cell(for: right, overlay: nil)
        }
        .aspectRatio(aspectRatio, contentMode: .fit)
    }

    @ViewBuilder
    private func tripleLayout(primary: URL, secondary: URL, tertiary: URL) -> some View {
        HStack(spacing: 2) {
            cell(for: primary, overlay: nil)
            VStack(spacing: 2) {
                cell(for: secondary, overlay: nil)
                cell(for: tertiary, overlay: nil)
            }
        }
        .aspectRatio(aspectRatio, contentMode: .fit)
    }

    @ViewBuilder
    private func quadLayout(_ urls: [URL]) -> some View {
        let visible = Array(urls.prefix(4))
        let extra = urls.count - visible.count
        VStack(spacing: 2) {
            HStack(spacing: 2) {
                cell(for: visible[0], overlay: nil)
                cell(for: visible[1], overlay: nil)
            }
            HStack(spacing: 2) {
                cell(for: visible[2], overlay: nil)
                cell(
                    for: visible[3],
                    overlay: extra > 0 ? "+\(extra)" : nil
                )
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }

    @ViewBuilder
    private func cell(for url: URL, overlay: String?) -> some View {
        Button {
            (tapHandler ?? renderer.callbacks.onImageTap)(url)
        } label: {
            // Loading + failure handling lives in the renderer-installed
            // `imageLoader` (see swiftui/content-core). Apps wiring Kingfisher
            // or Nuke get all grid cells routed through their loader without
            // changing the grid layout.
            NostrImageView(url: url)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .overlay {
                    if let overlay {
                        ZStack {
                            Color.black.opacity(0.45)
                            Text(overlay)
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Image")
    }
}
