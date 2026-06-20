import SwiftUI
import PhotosUI

struct PhotoSelectionScreen: View {
    let onDone: ([UIImage]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var isPresented = false
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var isLoading = false

    private var maxSelection: Int {
        guard let configJSON = NMPBridge.shared.pickerConfigJSON(),
              let data = configJSON.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let limit = obj["max_selection"] as? Int else { return 0 }
        return limit
    }

    var body: some View {
        ZStack {
            Color.olasBackground.ignoresSafeArea()
            if isLoading {
                ProgressView().tint(Color.olasText1)
            }
        }
        .photosPicker(
            isPresented: $isPresented,
            selection: $selectedItems,
            maxSelectionCount: maxSelection,
            matching: .images
        )
        .onAppear {
            #if DEBUG
            // CI bypass: if debug_compose_photo.jpg exists in Documents, skip the picker.
            if let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                let debugPhoto = docs.appendingPathComponent("debug_compose_photo.jpg")
                if FileManager.default.fileExists(atPath: debugPhoto.path),
                   let img = UIImage(contentsOfFile: debugPhoto.path) {
                    isLoading = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        isLoading = false
                        onDone([img])
                    }
                    return
                }
            }
            #endif
            isPresented = true
        }
        .onChange(of: isPresented) { _, newValue in
            if !newValue && selectedItems.isEmpty {
                dismiss()
            }
        }
        .onChange(of: selectedItems) { _, items in
            guard !items.isEmpty else { return }
            isLoading = true
            Task {
                var images: [UIImage] = []
                for item in items {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let img = UIImage(data: data) {
                        images.append(img)
                    }
                }
                await MainActor.run {
                    isLoading = false
                    onDone(images)
                }
            }
        }
    }
}
