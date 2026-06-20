import SwiftUI

struct ProfileGridView: View {
    let posts: [PhotoPost]
    let onSelect: (PhotoPost) -> Void

    @Environment(\.zoomNamespace) private var zoomNamespace

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 1), count: 3)

    var body: some View {
        if posts.isEmpty {
            VStack(spacing: OlasSpacing.md) {
                Image(systemName: "camera")
                    .font(.system(size: 40, weight: .ultraLight))
                    .foregroundStyle(Color.olasText3)
                Text("No photos yet")
                    .font(OlasFont.subheadline())
                    .foregroundStyle(Color.olasText2)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, OlasSpacing.xxl * 2)
        } else {
            LazyVGrid(columns: columns, spacing: 1) {
                ForEach(posts) { post in
                    gridCell(post)
                }
            }
            .padding(.bottom, 100) // clear floating tab bar
        }
    }

    private func gridCell(_ post: PhotoPost) -> some View {
        Button {
            onSelect(post)
        } label: {
            GeometryReader { geo in
                ZStack(alignment: .topTrailing) {
                    CachedImage(
                        url: URL(string: post.images.first?.url ?? ""),
                        loading: { Rectangle().fill(Color.olasSurface2) },
                        failure: {
                            ZStack {
                                Rectangle().fill(Color.olasSurface2)
                                Image(systemName: "photo")
                                    .font(.system(size: 20, weight: .thin))
                                    .foregroundStyle(Color.olasText3)
                            }
                        }
                    )
                    .frame(width: geo.size.width, height: geo.size.width)
                    .clipped()
                    // Mark as zoom source; id matches what ProfileView passes to photoLift.
                    .zoomSource(id: "profile-\(post.id)-0", namespace: zoomNamespace)

                    if post.images.count > 1 {
                        HStack(spacing: 2) {
                            Image(systemName: "square.on.square")
                                .font(.system(size: 10, weight: .semibold))
                            Text("\(post.images.count)")
                                .font(OlasFont.captionSmall())
                        }
                        .foregroundStyle(.white)
                        .padding(4)
                        .background(.black.opacity(0.5), in: RoundedRectangle(cornerRadius: 4))
                        .padding(4)
                    }
                }
            }
            .aspectRatio(1, contentMode: .fit)
        }
        .buttonStyle(.plain)
    }
}
