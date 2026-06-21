import SwiftUI
import PhotosUI

enum ComposeStep {
    case camera
    case photoPicker
    case editPhoto([UIImage])
    case caption([UIImage], UIImage) // selected images, filtered preview
}

struct ComposeNavigator: View {
    @Environment(\.dismiss) private var dismiss
    @State private var step: ComposeStep = .camera
    @State private var showLibrary = false

    init() {
        // Verify compose step order from Rust matches what Swift expects.
        if let stepsJSON = NMPBridge.shared.composeStepsJSON(),
           let data = stepsJSON.data(using: .utf8),
           let steps = try? JSONDecoder().decode([String].self, from: data) {
            assert(steps == ["photo_picker", "edit_photo", "caption"], "Rust compose steps mismatch: \(steps)")
        }
    }

    var body: some View {
        NavigationStack {
            switch step {
            case .camera:
                CameraView(
                    onCapture: { images in
                        guard !images.isEmpty else { return }
                        step = .editPhoto(images)
                    },
                    onPickFromLibrary: { showLibrary = true }
                )
                .navigationTitle("Camera")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                            .foregroundStyle(Color.olasText2)
                    }
                }
                // Library presented as a full-screen cover so its own dismiss()
                // hides the cover rather than the whole compose sheet.
                .fullScreenCover(isPresented: $showLibrary) {
                    PhotoSelectionScreen { images in
                        showLibrary = false
                        guard !images.isEmpty else { return }
                        step = .editPhoto(images)
                    }
                }

            case .photoPicker:
                // Retained for potential deep-link entry; normally reached via
                // the .fullScreenCover on .camera.
                PhotoSelectionScreen { images in
                    guard !images.isEmpty else { step = .camera; return }
                    step = .editPhoto(images)
                }
                .navigationTitle("Library")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Back") { step = .camera }
                            .foregroundStyle(Color.olasText2)
                    }
                }

            case .editPhoto(let images):
                EditPhotoView(images: images) { filtered in
                    step = .caption(images, filtered)
                }
                .navigationTitle("Edit")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Back") { step = .camera }
                            .foregroundStyle(Color.olasText2)
                    }
                }

            case .caption(let images, let filtered):
                CaptionView(images: images, filteredPreview: filtered) {
                    dismiss()
                }
                .navigationTitle("New Post")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Back") {
                            guard case .caption(let imgs, _) = step else { return }
                            step = .editPhoto(imgs)
                        }
                        .foregroundStyle(Color.olasText2)
                    }
                }
            }
        }
        .background(Color.olasBackground)
        .preferredColorScheme(.dark)
    }
}
