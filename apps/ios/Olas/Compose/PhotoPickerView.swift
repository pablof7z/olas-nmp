import SwiftUI
import PhotosUI

struct PhotoSelectionScreen: View {
    let onDone: ([UIImage]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var isPresented = true
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var isLoading = false

    var body: some View {
        ZStack {
            Color.olasBackground.ignoresSafeArea()
            if isLoading {
                ProgressView().tint(Color.olasText1)
            }
        }
        // NMP-GAP(#23): Picker constraints (max selection, ingestion policy) must come from Rust config.
        .photosPicker(
            isPresented: $isPresented,
            selection: $selectedItems,
            maxSelectionCount: 10,
            matching: .images
        )
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
