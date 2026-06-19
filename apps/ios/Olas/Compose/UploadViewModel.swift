import Foundation
import UIKit
import ImageIO
import UniformTypeIdentifiers

/// Image processing helpers for picture posts.
/// All network / signing / hashing lives in Rust (NMP doctrine).
/// This file owns only: downsample, explicit EXIF strip, temp-file I/O.
enum ImageEncoder {

    /// Downsample `image` so the long edge is ≤ `maxDimension`, then encode to
    /// JPEG at `quality` with ALL EXIF metadata stripped.
    ///
    /// We always strip EXIF regardless of whether the user enabled location.
    /// Location is communicated only via the NIP-52 "g" Nostr tag, which the
    /// caller computes at 4-char geohash precision.
    static func encodeStrippingEXIF(
        _ image: UIImage,
        maxDimension: CGFloat,
        quality: CGFloat
    ) async -> Data? {
        await Task.detached(priority: .utility) {
            // 1. Downsample to a fresh CGImage (strips metadata by redrawing).
            guard let cgSource = image.cgImage else { return nil }
            let w = CGFloat(cgSource.width)
            let h = CGFloat(cgSource.height)
            let maxSide = max(w, h)
            let scale = maxSide > maxDimension ? maxDimension / maxSide : 1.0
            let newW = Int(w * scale)
            let newH = Int(h * scale)

            let colorSpace = cgSource.colorSpace ?? CGColorSpaceCreateDeviceRGB()
            guard let ctx = CGContext(
                data: nil,
                width: newW, height: newH,
                bitsPerComponent: 8,
                bytesPerRow: 0,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
            ) else { return nil }
            ctx.draw(cgSource, in: CGRect(x: 0, y: 0, width: newW, height: newH))
            guard let cgOut = ctx.makeImage() else { return nil }

            // 2. Encode via CGImageDestination with ONLY compression quality —
            // no EXIF, no GPS, no date, no camera info.
            let data = NSMutableData()
            guard let dest = CGImageDestinationCreateWithData(
                data,
                UTType.jpeg.identifier as CFString,
                1, nil
            ) else { return nil }
            CGImageDestinationAddImage(dest, cgOut, [
                kCGImageDestinationLossyCompressionQuality: quality
            ] as CFDictionary)
            guard CGImageDestinationFinalize(dest) else { return nil }
            return data as Data
        }.value
    }

    /// Write `data` to a temp file, returning the URL.
    static func writeTempFile(_ data: Data, ext: String = "jpg") throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(UUID().uuidString).\(ext)")
        try data.write(to: url)
        return url
    }

    /// Pixel dimensions of `image` as "WxH" after downsampling.
    static func dim(_ image: UIImage, maxDimension: CGFloat) -> String {
        let size = image.size
        let maxSide = max(size.width, size.height)
        let scale = maxSide > maxDimension ? maxDimension / maxSide : 1.0
        return "\(Int(size.width * scale))x\(Int(size.height * scale))"
    }
}
