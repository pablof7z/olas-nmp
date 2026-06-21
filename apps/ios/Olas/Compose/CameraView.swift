import AVFoundation
import SwiftUI

// MARK: - Preview Layer Wrapper

private struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        let v = PreviewUIView()
        v.previewLayer.session = session
        v.previewLayer.videoGravity = .resizeAspectFill
        return v
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}

    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

// MARK: - Camera Model

@MainActor
final class CameraModel: NSObject, ObservableObject {
    @Published var isPermissionDenied = false
    @Published var isCapturing = false
    // True once the session is running with an active video connection. The
    // shutter is gated on this to avoid an NSException if tapped before config.
    @Published var isSessionReady = false

    // AVCaptureSession is internally thread-safe when driven from sessionQueue.
    // nonisolated(unsafe) shares the reference with the preview layer without
    // crossing the @MainActor isolation boundary.
    nonisolated(unsafe) let session = AVCaptureSession()
    nonisolated(unsafe) private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "io.f7z.olas.camera.session")
    private var lensFacing: AVCaptureDevice.Position = .back
    var onCapture: (UIImage) -> Void = { _ in }

    func requestAndStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configure(position: lensFacing)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    if granted { self.configure(position: self.lensFacing) }
                    else { self.isPermissionDenied = true }
                }
            }
        default:
            isPermissionDenied = true
        }
    }

    func stop() {
        let sess = session
        isSessionReady = false
        sessionQueue.async { if sess.isRunning { sess.stopRunning() } }
    }

    func toggleLens() {
        lensFacing = lensFacing == .back ? .front : .back
        configure(position: lensFacing)
    }

    func capturePhoto() {
        // Guard against tapping the shutter before the session has an active
        // video connection — capturing then raises an NSException.
        guard isSessionReady, !isCapturing,
              photoOutput.connection(with: .video)?.isActive == true else { return }
        isCapturing = true
        let settings = AVCapturePhotoSettings()
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    private func configure(position: AVCaptureDevice.Position) {
        let sess = session
        let out  = photoOutput
        sessionQueue.async {
            sess.beginConfiguration()
            sess.inputs.forEach { sess.removeInput($0) }
            guard
                let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
                let input  = try? AVCaptureDeviceInput(device: device)
            else { sess.commitConfiguration(); return }
            if sess.canAddInput(input) { sess.addInput(input) }
            if !sess.outputs.contains(out), sess.canAddOutput(out) { sess.addOutput(out) }
            sess.commitConfiguration()
            if !sess.isRunning { sess.startRunning() }
            let ready = sess.isRunning
            Task { @MainActor [weak self] in self?.isSessionReady = ready }
        }
    }
}

extension CameraModel: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else {
            Task { @MainActor [weak self] in self?.isCapturing = false }
            return
        }
        Task { @MainActor [weak self] in
            self?.isCapturing = false
            self?.onCapture(image)
        }
    }
}

// MARK: - Camera View

struct CameraView: View {
    let onCapture: ([UIImage]) -> Void
    let onPickFromLibrary: () -> Void

    @StateObject private var model = CameraModel()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if model.isPermissionDenied {
                deniedView
            } else {
                CameraPreviewView(session: model.session)
                    .ignoresSafeArea()
                controlsOverlay
            }
        }
        .onAppear {
            model.onCapture = { image in onCapture([image]) }
            model.requestAndStart()
        }
        .onDisappear { model.stop() }
    }

    // MARK: Controls overlay

    private var controlsOverlay: some View {
        VStack {
            Spacer()
            HStack(alignment: .center, spacing: 0) {
                Button(action: onPickFromLibrary) {
                    VStack(spacing: OlasSpacing.xxs) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 26))
                        Text("Library").font(OlasFont.captionSmall())
                    }
                    .foregroundStyle(.white)
                }
                .frame(maxWidth: .infinity)

                Button { model.capturePhoto() } label: {
                    ZStack {
                        Circle().stroke(Color.white.opacity(0.8), lineWidth: 3).frame(width: 84, height: 84)
                        Circle().fill(.white).frame(width: 72, height: 72)
                    }
                }
                .disabled(model.isCapturing || !model.isSessionReady)
                .opacity(model.isSessionReady ? 1 : 0.4)
                .frame(maxWidth: .infinity)

                Button { model.toggleLens() } label: {
                    VStack(spacing: OlasSpacing.xxs) {
                        Image(systemName: "arrow.triangle.2.circlepath.camera")
                            .font(.system(size: 26))
                        Text("Flip").font(OlasFont.captionSmall())
                    }
                    .foregroundStyle(.white)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.bottom, OlasSpacing.xxxl)
        }
    }

    // MARK: Permission-denied state

    private var deniedView: some View {
        VStack(spacing: OlasSpacing.lg) {
            Spacer()
            Image(systemName: "camera.slash")
                .font(.system(size: 56))
                .foregroundStyle(Color.olasText3)
            Text("Camera Access Required")
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasText1)
            Text("Enable camera access in Settings to take photos inside Olas.")
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText2)
                .multilineTextAlignment(.center)
                .padding(.horizontal, OlasSpacing.xl)
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .font(OlasFont.body())
            .foregroundStyle(Color.olasBlue)
            Spacer()
            Divider().background(Color.olasBorder)
            Button("Choose from Library instead", action: onPickFromLibrary)
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText2)
                .padding(.bottom, OlasSpacing.xl)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.olasBackground)
    }
}
