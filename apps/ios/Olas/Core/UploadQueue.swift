import Foundation
import SwiftUI
import UIKit

/// Global background-upload queue. Shown as a miniplayer while active.
/// Owns the upload lifecycle so the compose sheet can dismiss immediately.
///
/// P0-B: supports multi-image posts. Each image is encoded independently,
/// uploaded to Blossom, and the full descriptor array is passed to Rust's
/// `olas_picture_post_publish_json` which emits ONE kind:20 with multiple
/// NIP-68 `imeta` tags.
@Observable @MainActor
final class UploadQueue {
    static let shared = UploadQueue()

    struct ImageEntry {
        let image: UIImage
        let altText: String?
    }

    struct ActiveUpload {
        let thumbnail: UIImage
        var step: UploadStep = .encoding
    }

    var active: ActiveUpload?

    private init() {}

    /// Start an upload of one or more images and return immediately.
    /// The compose sheet dismisses; the miniplayer tracks progress.
    func enqueue(images: [ImageEntry], caption: String, geohash: String?) {
        guard !images.isEmpty else { return }
        let thumb = makeThumbnail(images[0].image)
        active = ActiveUpload(thumbnail: thumb)
        Task { await run(images: images, caption: caption, geohash: geohash) }
    }

    /// Convenience wrapper for single-image callers (backward compatibility).
    func enqueue(image: UIImage, caption: String, altText: String?, geohash: String?) {
        enqueue(images: [ImageEntry(image: image, altText: altText)], caption: caption, geohash: geohash)
    }

    private func run(images: [ImageEntry], caption: String, geohash: String?) async {
        setStep(.encoding)

        struct MediaConfig: Decodable { let max_dimension: Int; let jpeg_quality: Double }
        guard let configJSON = NMPBridge.shared.mediaUploadConfigJSON(),
              let configData = configJSON.data(using: .utf8),
              let config = try? JSONDecoder().decode(MediaConfig.self, from: configData) else {
            setStep(.error("Media config unavailable.")); return
        }
        let maxDimension = CGFloat(config.max_dimension)
        let jpegQuality = CGFloat(config.jpeg_quality)

        // Determine Blossom server list (blossom.band first for serving).
        let configured = NMPBridge.shared.blossomServerURL
        let defaultServers = ["https://blossom.band", "https://blossom.primal.net"]
        let isDefault = configured.isEmpty || defaultServers.contains(configured)
        var servers = isDefault ? defaultServers : [configured] + defaultServers
        var seen = Set<String>()
        servers = servers.filter { seen.insert($0).inserted }

        setStep(.uploading(0))

        // Upload each image independently; collect descriptors.
        var uploadedImages: [(descriptorJSON: String, alt: String?, dim: String?)] = []
        for (index, entry) in images.enumerated() {
            let progress = Double(index) / Double(images.count)
            setStep(.uploading(progress))

            guard let jpeg = await ImageEncoder.encodeStrippingEXIF(
                entry.image, maxDimension: maxDimension, quality: jpegQuality
            ) else {
                setStep(.error("Couldn't encode image \(index + 1).")); return
            }
            let tmpURL: URL
            do { tmpURL = try ImageEncoder.writeTempFile(jpeg) }
            catch { setStep(.error("Couldn't write temp file.")); return }
            defer { try? FileManager.default.removeItem(at: tmpURL) }

            let dim = ImageEncoder.dim(entry.image, maxDimension: maxDimension)

            var terminal: NMPBridge.ActionTerminal?
            var lastError = "No servers tried"
            for server in servers {
                guard let uploadInput = NMPBridge.shared.blossomUploadInputJSON(
                    filePath: tmpURL.path, mime: "image/jpeg", serverURL: server
                ) else { continue }
                let result = await NMPBridge.shared.dispatchAndAwaitResult(
                    namespace: "nmp.blossom.upload", json: uploadInput
                )
                if let r = result, r.succeeded, r.resultJSON != "null" {
                    terminal = r; break
                }
                lastError = "[\(server)] \(result.map { String($0.resultJSON.prefix(80)) } ?? "dispatch rejected")"
            }
            guard let terminal else {
                setStep(.error("Upload failed (image \(index + 1)): \(lastError)")); return
            }
            uploadedImages.append((descriptorJSON: terminal.resultJSON, alt: entry.altText, dim: dim))
        }

        setStep(.publishing)

        guard let publishInput = NMPBridge.shared.picturePostPublishJSON(
            uploadedImages: uploadedImages,
            caption: caption,
            geohash: geohash
        ) else { setStep(.error("Couldn't prepare post.")); return }

        guard let pubTerminal = await NMPBridge.shared.dispatchAndAwaitResult(
            namespace: "nmp.publish", json: publishInput
        ), pubTerminal.succeeded else {
            setStep(.error("Publish failed.")); return
        }

        setStep(.done)
        Task { try? await Task.sleep(for: .seconds(3)); clearTerminal() }
    }

    func clearTerminal() {
        switch active?.step {
        case .done, .error:
            withAnimation(.olasStandard) { active = nil }
        default:
            break
        }
    }

    private func setStep(_ step: UploadStep) {
        active?.step = step
    }

    private func makeThumbnail(_ image: UIImage) -> UIImage {
        let side: CGFloat = 52
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        return renderer.image { _ in
            let size = image.size
            let scale = max(side / size.width, side / size.height)
            let sw = size.width * scale, sh = size.height * scale
            image.draw(in: CGRect(x: (side - sw) / 2, y: (side - sh) / 2, width: sw, height: sh))
        }
    }
}
