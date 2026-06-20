import SwiftUI

// MARK: - BlurHash Decoder
//
// Self-contained ~120-line native BlurHash decoder (no external dependency).
// Spec: https://github.com/woltapp/blurhash/blob/master/Algorithm.md
//
// Usage:
//   let image = BlurHashDecoder.decode("LGF5?xYk^6#M@-5c,1Ex@@or[j6o", width: 32, height: 32)

enum BlurHashDecoder {

    // MARK: - Process-wide memoisation cache
    //
    // NSCache is thread-safe: concurrent reads and writes from any queue are
    // safe without additional locking. We cache the decoded CGImage (a class /
    // AnyObject) keyed by "<hash>:<w>x<h>" so that SwiftUI re-inits during
    // scroll — which happen on the main thread — return instantly without
    // re-running the inverse-DCT.
    private static let imageCache: NSCache<NSString, AnyObject> = {
        let c = NSCache<NSString, AnyObject>()
        c.countLimit = 200   // ~200 × 32×32×4 ≈ 800 KB resident
        return c
    }()

    /// Memoised decode: decodes once per unique (hash, size) pair; subsequent
    /// calls return the cached CGImage-backed Image with no extra work.
    /// Prefer this over `decode(_:width:height:)` inside SwiftUI view inits.
    static func cachedDecode(_ hash: String, width: Int = 32, height: Int = 32) -> Image? {
        let key = "\(hash):\(width)x\(height)" as NSString
        if let cached = imageCache.object(forKey: key) as? CGImage {
            return Image(decorative: cached, scale: 1, orientation: .up)
        }
        guard let pixels = decodeToPixels(hash, width: width, height: height) else { return nil }
        let bytesPerRow = width * 4
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cgImage = CGImage(
                width: width, height: height,
                bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: bytesPerRow,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider,
                decode: nil, shouldInterpolate: true,
                intent: .defaultIntent
              )
        else { return nil }
        imageCache.setObject(cgImage, forKey: key)
        return Image(decorative: cgImage, scale: 1, orientation: .up)
    }

    /// Decode a blurhash string into a SwiftUI Image at the given pixel dimensions.
    /// Returns nil when the hash is invalid or decoding fails.
    /// For use inside SwiftUI view inits, prefer `cachedDecode` to avoid
    /// repeating the inverse-DCT on every parent body pass.
    static func decode(_ hash: String, width: Int = 32, height: Int = 32) -> Image? {
        guard let pixels = decodeToPixels(hash, width: width, height: height) else { return nil }
        let bytesPerRow = width * 4
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cgImage = CGImage(
                width: width, height: height,
                bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: bytesPerRow,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider,
                decode: nil, shouldInterpolate: true,
                intent: .defaultIntent
              )
        else { return nil }
        return Image(decorative: cgImage, scale: 1, orientation: .up)
    }

    /// Core decode: returns RGBA bytes or nil on invalid input.
    private static func decodeToPixels(_ hash: String, width: Int, height: Int) -> [UInt8]? {
        let chars = Array(hash)
        guard chars.count >= 6 else { return nil }

        guard let sizeFlag = base83Decode(chars[0]) else { return nil }
        let numY = Int(sizeFlag / 9) + 1
        let numX = Int(sizeFlag % 9) + 1
        let expectedLength = 4 + 2 * numX * numY
        guard chars.count == expectedLength else { return nil }

        guard let quantisedMaximum = base83Decode(chars[1]) else { return nil }
        let maxValue = Float(quantisedMaximum + 1) / 166.0

        var colors = [(Float, Float, Float)]()
        colors.reserveCapacity(numX * numY)

        for i in 0 ..< numX * numY {
            if i == 0 {
                guard let val = base83Decode(chars[2], chars[3], chars[4], chars[5]) else { return nil }
                colors.append(decodeDCValue(val))
            } else {
                let offset = 4 + i * 2
                guard let val = base83Decode(chars[offset], chars[offset + 1]) else { return nil }
                colors.append(decodeACValue(val, maxValue: maxValue))
            }
        }

        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        for y in 0 ..< height {
            for x in 0 ..< width {
                var r: Float = 0, g: Float = 0, b: Float = 0
                for j in 0 ..< numY {
                    for i in 0 ..< numX {
                        let basis = cos((.pi * Float(x) * Float(i)) / Float(width))
                                  * cos((.pi * Float(y) * Float(j)) / Float(height))
                        let (cr, cg, cb) = colors[j * numX + i]
                        r += cr * basis
                        g += cg * basis
                        b += cb * basis
                    }
                }
                let idx = (y * width + x) * 4
                pixels[idx]     = linearToSRGB(r)
                pixels[idx + 1] = linearToSRGB(g)
                pixels[idx + 2] = linearToSRGB(b)
                pixels[idx + 3] = 255
            }
        }
        return pixels
    }

    // MARK: - Helpers

    private static func decodeDCValue(_ val: Int) -> (Float, Float, Float) {
        let r = val >> 16
        let g = (val >> 8) & 255
        let b = val & 255
        return (sRGBToLinear(r), sRGBToLinear(g), sRGBToLinear(b))
    }

    private static func decodeACValue(_ val: Int, maxValue: Float) -> (Float, Float, Float) {
        let rQ = val / (19 * 19)
        let gQ = (val / 19) % 19
        let bQ = val % 19
        return (
            signedPow(Float(rQ) - 9, 2) * maxValue,
            signedPow(Float(gQ) - 9, 2) * maxValue,
            signedPow(Float(bQ) - 9, 2) * maxValue
        )
    }

    private static func signedPow(_ x: Float, _ p: Float) -> Float {
        x < 0 ? -pow(-x, p) : pow(x, p)
    }

    private static func sRGBToLinear(_ value: Int) -> Float {
        let f = Float(value) / 255.0
        return f <= 0.04045 ? f / 12.92 : pow((f + 0.055) / 1.055, 2.4)
    }

    private static func linearToSRGB(_ value: Float) -> UInt8 {
        let clamped = max(0, min(1, value))
        let srgb = clamped <= 0.0031308
            ? clamped * 12.92
            : 1.055 * pow(clamped, 1.0 / 2.4) - 0.055
        return UInt8(srgb * 255 + 0.5)
    }

    // MARK: - Base83

    private static let base83Chars =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private static func base83Decode(_ chars: Character...) -> Int? {
        var value = 0
        for c in chars {
            guard let idx = base83Chars.firstIndex(of: c) else { return nil }
            value = value * 83 + base83Chars.distance(from: base83Chars.startIndex, to: idx)
        }
        return value
    }
}
