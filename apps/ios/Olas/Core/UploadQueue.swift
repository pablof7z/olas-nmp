import Foundation
import SwiftUI
import UIKit

/// Global background-upload queue. Shown as a miniplayer while active.
/// Owns the upload lifecycle so the compose sheet can dismiss immediately.
@Observable @MainActor
final class UploadQueue {
    static let shared = UploadQueue()

    struct ActiveUpload {
        let thumbnail: UIImage
        var step: UploadStep = .encoding
    }

    var active: ActiveUpload?

    private init() {}

    /// Start an upload and return immediately. The compose sheet dismisses; the
    /// miniplayer tracks progress from `ContentView`.
    func enqueue(image: UIImage, caption: String, altText: String?, geohash: String?) {
        let thumb = makeThumbnail(image)
        active = ActiveUpload(thumbnail: thumb)
        Task { await run(image: image, caption: caption, altText: altText, geohash: geohash) }
    }

    private func run(image: UIImage, caption: String, altText: String?, geohash: String?) async {
        // Encode + strip EXIF — load config from Rust.
        setStep(.encoding)
        struct MediaConfig: Decodable { let max_dimension: Int; let jpeg_quality: Double }
        guard let configJSON = NMPBridge.shared.mediaUploadConfigJSON(),
              let configData = configJSON.data(using: .utf8),
              let config = try? JSONDecoder().decode(MediaConfig.self, from: configData) else {
            setStep(.error("Media config unavailable.")); return
        }
        let maxDimension = CGFloat(config.max_dimension)
        let jpegQuality = CGFloat(config.jpeg_quality)
        guard let jpeg = await ImageEncoder.encodeStrippingEXIF(image, maxDimension: maxDimension, quality: jpegQuality) else {
            setStep(.error("Couldn't encode image.")); return
        }
        let tmpURL: URL
        do { tmpURL = try ImageEncoder.writeTempFile(jpeg) }
        catch { setStep(.error("Couldn't write temp file.")); return }
        defer { try? FileManager.default.removeItem(at: tmpURL) }

        setStep(.uploading(0))

        // blossom.band first (publicly serves files); primal.net accepts uploads but returns 404 on GET.
        // The user-configured server overrides this list when it differs from the defaults.
        let configured = NMPBridge.shared.blossomServerURL
        let defaultServers = ["https://blossom.band", "https://blossom.primal.net"]
        let isDefault = configured.isEmpty || defaultServers.contains(configured)
        var servers = isDefault ? defaultServers : [configured] + defaultServers
        // dedupe while preserving order
        var seen = Set<String>()
        servers = servers.filter { seen.insert($0).inserted }

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
        guard let terminal else { setStep(.error("Upload failed: \(lastError)")); return }

        setStep(.publishing)

        let dim = ImageEncoder.dim(image, maxDimension: maxDimension)
        guard let publishInput = NMPBridge.shared.picturePostPublishJSON(
            blossomResultJSON: terminal.resultJSON,
            caption: caption,
            alt: altText,
            dim: dim,
            geohash: geohash
        ) else { setStep(.error("Couldn't prepare post.")); return }

        guard let pubTerminal = await NMPBridge.shared.dispatchAndAwaitResult(
            namespace: "nmp.publish", json: publishInput
        ), pubTerminal.succeeded else {
            setStep(.error("Publish failed.")); return
        }

        setStep(.done)
        OlasHaptics.notificationSuccess()
        OlasSound.shutterSoft()
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
