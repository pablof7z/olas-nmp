import SwiftUI

struct FullscreenImageView: View {
    let post: PhotoPost
    let initialIndex: Int

    @Environment(\.dismiss) private var dismiss
    @State private var dragOffset: CGFloat = 0
    @State private var magnification: CGFloat = 1.0
    @State private var currentIndex: Int

    init(post: PhotoPost, initialIndex: Int) {
        self.post = post
        self.initialIndex = initialIndex
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

            // Dismiss indicator
            VStack {
                HStack {
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
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

                // Dot indicator for carousel
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
        .offset(y: dragOffset)
        .opacity(Double(1 - abs(dragOffset) / 300))
        .gesture(dragToDismiss)
    }

    private func singleImageView(_ image: ImageMeta) -> some View {
        AsyncImage(url: URL(string: image.url)) { img in
            img
                .resizable()
                .scaledToFit()
                .scaleEffect(max(1.0, min(4.0, magnification)))
                .gesture(magnifyGesture)
        } placeholder: {
            ProgressView()
                .tint(.white)
        }
    }

    @State private var scrollPosition: Int? = nil

    private var carouselView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(Array(post.images.enumerated()), id: \.offset) { idx, image in
                    AsyncImage(url: URL(string: image.url)) { img in
                        img
                            .resizable()
                            .scaledToFit()
                            .scaleEffect(max(1.0, min(4.0, idx == currentIndex ? magnification : 1.0)))
                            .gesture(magnifyGesture)
                    } placeholder: {
                        ProgressView().tint(.white)
                    }
                    .containerRelativeFrame(.horizontal)
                }
            }
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $scrollPosition)
        .onChange(of: scrollPosition) { _, newVal in
            if let v = newVal { currentIndex = v }
        }
    }

    private var magnifyGesture: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                magnification = value.magnification
            }
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
                let translation = value.translation.height
                if translation > 0 {
                    dragOffset = translation
                }
            }
            .onEnded { value in
                if value.translation.height > 100 {
                    dismiss()
                } else {
                    withAnimation(.olasStandard) {
                        dragOffset = 0
                    }
                }
            }
    }
}
