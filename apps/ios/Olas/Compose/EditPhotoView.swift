import SwiftUI

struct EditPhotoView: View {
    let images: [UIImage]
    let onNext: (UIImage) -> Void

    @State private var currentIndex = 0
    @State private var selectedFilterId = "original"
    @State private var filterIntensity: Float = 0.75
    @State private var filteredImage: UIImage?
    @State private var showAdjust = false
    @State private var brightness: Double = 0
    @State private var contrast: Double = 1
    @State private var saturation: Double = 1

    private var displayImage: UIImage {
        filteredImage ?? images[currentIndex]
    }

    var body: some View {
        VStack(spacing: 0) {
            // Preview
            Image(uiImage: displayImage)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity, maxHeight: 360)
                .background(Color.black)
                .clipped()

            // Thumbnail strip if multi
            if images.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: OlasSpacing.xs) {
                        ForEach(Array(images.enumerated()), id: \.offset) { idx, img in
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 50, height: 50)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 6)
                                        .stroke(idx == currentIndex ? Color.white : Color.clear, lineWidth: 2)
                                )
                                .onTapGesture { currentIndex = idx }
                        }
                    }
                    .padding(.horizontal, OlasSpacing.md)
                    .padding(.vertical, OlasSpacing.sm)
                }
                .background(Color.olasSurface)
            }

            // Filter / Adjust toggle
            HStack(spacing: 0) {
                tabToggle(title: "Filter", isActive: !showAdjust) { showAdjust = false }
                tabToggle(title: "Adjust", isActive: showAdjust) { showAdjust = true }
            }
            .background(Color.olasSurface)

            if showAdjust {
                adjustControls
            } else {
                FilterCarouselView(
                    sourceImage: images[currentIndex],
                    selectedFilterId: $selectedFilterId,
                    intensity: $filterIntensity
                ) { filtered in
                    filteredImage = filtered
                }
            }

            Spacer()

            Button {
                onNext(filteredImage ?? images[currentIndex])
            } label: {
                Text("Next")
                    .font(OlasFont.headline())
                    .foregroundStyle(Color.olasBackground)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(OlasPressedButtonStyle())
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.bottom, OlasSpacing.xl)
        }
        .background(Color.olasBackground)
    }

    private var adjustControls: some View {
        VStack(spacing: OlasSpacing.md) {
            adjustSlider(label: "Brightness", value: $brightness, range: -0.5...0.5)
            adjustSlider(label: "Contrast", value: $contrast, range: 0.5...2.0)
            adjustSlider(label: "Saturation", value: $saturation, range: 0...2.0)
        }
        .padding(.horizontal, OlasSpacing.xl)
        .padding(.top, OlasSpacing.md)
    }

    private func adjustSlider(label: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        HStack {
            Text(label)
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText2)
                .frame(width: 90, alignment: .leading)
            Slider(value: value, in: range)
                .tint(Color.olasText1)
        }
    }

    private func tabToggle(title: String, isActive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(OlasFont.subheadline())
                .foregroundStyle(isActive ? Color.olasText1 : Color.olasText3)
                .frame(maxWidth: .infinity, minHeight: 40)
                .overlay(alignment: .bottom) {
                    if isActive {
                        Rectangle()
                            .fill(Color.olasText1)
                            .frame(height: 1)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}
