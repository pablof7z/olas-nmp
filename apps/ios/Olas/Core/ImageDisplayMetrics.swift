import Foundation

enum ImageDisplayMetrics {
    static func aspectRatio(for image: ImageMeta) -> CGFloat {
        guard let w = image.dimensions?.width, let h = image.dimensions?.height, w > 0, h > 0 else {
            return 4.0 / 5.0 // default portrait
        }
        let ratio = CGFloat(w) / CGFloat(h)
        if ratio > 1.5 { return 3.0 / 2.0 }   // landscape capped at 3:2
        if ratio < 0.8 { return 4.0 / 5.0 }   // portrait capped at 4:5
        return ratio
    }
}
