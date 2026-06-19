import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins

struct FilterDefinition: Identifiable, @unchecked Sendable {
    let id: String
    let name: String
    let apply: (CIImage) -> CIImage?
}

enum PhotoFilters {
    static let context = CIContext()

    static let all: [FilterDefinition] = [
        FilterDefinition(id: "original", name: "Original") { $0 },
        FilterDefinition(id: "daylight", name: "Daylight") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 1.2; f.brightness = 0.05; f.contrast = 1.0
            return f.outputImage
        },
        FilterDefinition(id: "ember", name: "Ember") { img in
            let sepia = CIFilter.sepiaTone()
            sepia.inputImage = img; sepia.intensity = 0.6
            return sepia.outputImage
        },
        FilterDefinition(id: "dusk", name: "Dusk") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 0.8; f.brightness = -0.02; f.contrast = 1.05
            return f.outputImage
        },
        FilterDefinition(id: "mist", name: "Mist") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 1.0; f.brightness = 0.0; f.contrast = 0.85
            return f.outputImage
        },
        FilterDefinition(id: "chrome", name: "Chrome") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 0.9; f.brightness = 0.0; f.contrast = 1.2
            return f.outputImage
        },
        FilterDefinition(id: "film", name: "Film") { img in
            let f = CIFilter.photoEffectChrome()
            f.inputImage = img
            return f.outputImage
        },
        FilterDefinition(id: "fade", name: "Fade") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 0.9; f.brightness = 0.1; f.contrast = 0.85
            return f.outputImage
        },
        FilterDefinition(id: "arctic", name: "Arctic") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 0.3; f.brightness = 0.0; f.contrast = 1.0
            return f.outputImage
        },
        FilterDefinition(id: "copper", name: "Copper") { img in
            let f = CIFilter.hueAdjust()
            f.inputImage = img; f.angle = 0.3
            return f.outputImage
        },
        FilterDefinition(id: "veil", name: "Veil") { img in
            let f = CIFilter.colorControls()
            f.inputImage = img; f.saturation = 0.95; f.brightness = 0.15; f.contrast = 0.9
            return f.outputImage
        },
        FilterDefinition(id: "bloom", name: "Bloom") { img in
            let f = CIFilter.bloom()
            f.inputImage = img; f.intensity = 0.7; f.radius = 10
            return f.outputImage
        }
    ]

    static func apply(_ filter: FilterDefinition, to image: UIImage, intensity: Float = 1.0) -> UIImage? {
        guard let ciImage = CIImage(image: image) else { return nil }
        guard let output = filter.apply(ciImage) else { return image }
        guard let cgImage = context.createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage, scale: image.scale, orientation: image.imageOrientation)
    }
}

struct FilterCarouselView: View {
    let sourceImage: UIImage
    @Binding var selectedFilterId: String
    @Binding var intensity: Float
    let onFiltered: (UIImage) -> Void

    @State private var thumbnails: [String: UIImage] = [:]

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: OlasSpacing.sm) {
                    ForEach(PhotoFilters.all) { filter in
                        filterCell(filter)
                    }
                }
                .padding(.horizontal, OlasSpacing.md)
                .padding(.vertical, OlasSpacing.md)
            }

            // Intensity slider
            if selectedFilterId != "original" {
                HStack {
                    Text("Intensity")
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText2)
                    Slider(value: Binding(
                        get: { Double(intensity) },
                        set: { intensity = Float($0); applySelected() }
                    ), in: 0...1)
                    .tint(Color.olasText1)
                }
                .padding(.horizontal, OlasSpacing.xl)
                .padding(.bottom, OlasSpacing.sm)
            }
        }
        .task { await generateThumbnails() }
    }

    private func filterCell(_ filter: FilterDefinition) -> some View {
        let isSelected = selectedFilterId == filter.id
        return Button {
            selectedFilterId = filter.id
            applySelected()
        } label: {
            VStack(spacing: OlasSpacing.xxs) {
                Group {
                    if let thumb = thumbnails[filter.id] {
                        Image(uiImage: thumb)
                            .resizable()
                            .scaledToFill()
                    } else {
                        Rectangle().fill(Color.olasSurface2)
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isSelected ? Color.white : Color.clear, lineWidth: 2)
                )

                Text(filter.name)
                    .font(OlasFont.captionSmall())
                    .foregroundStyle(isSelected ? Color.olasText1 : Color.olasText3)
            }
        }
        .buttonStyle(.plain)
        .animation(.olasStandard, value: isSelected)
    }

    private func applySelected() {
        guard let filter = PhotoFilters.all.first(where: { $0.id == selectedFilterId }),
              let result = PhotoFilters.apply(filter, to: sourceImage, intensity: intensity) else { return }
        onFiltered(result)
    }

    private func generateThumbnails() async {
        let small = await resized(sourceImage, to: CGSize(width: 64, height: 64))
        for filter in PhotoFilters.all {
            guard let ciImg = CIImage(image: small) else { continue }
            let output = filter.apply(ciImg) ?? ciImg
            if let cg = PhotoFilters.context.createCGImage(output, from: output.extent) {
                thumbnails[filter.id] = UIImage(cgImage: cg)
            }
        }
    }

    private func resized(_ image: UIImage, to size: CGSize) async -> UIImage {
        await Task.detached(priority: .utility) {
            UIGraphicsBeginImageContextWithOptions(size, false, 1)
            image.draw(in: CGRect(origin: .zero, size: size))
            let result = UIGraphicsGetImageFromCurrentImageContext() ?? image
            UIGraphicsEndImageContext()
            return result
        }.value
    }
}
