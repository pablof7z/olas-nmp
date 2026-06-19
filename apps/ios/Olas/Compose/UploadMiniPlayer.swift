import SwiftUI

/// Floating progress banner shown above the tab bar while a photo is uploading.
struct UploadMiniPlayer: View {
    let upload: UploadQueue.ActiveUpload

    @State private var appeared = false

    var body: some View {
        HStack(spacing: 12) {
            // Thumbnail
            Image(uiImage: upload.thumbnail)
                .resizable()
                .scaledToFill()
                .frame(width: 36, height: 36)
                .clipShape(RoundedRectangle(cornerRadius: 6))

            // Status
            VStack(alignment: .leading, spacing: 3) {
                Text(statusLabel)
                    .font(OlasFont.caption())
                    .foregroundStyle(Color.olasText1)
                    .animation(.none, value: statusLabel)

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.olasText3.opacity(0.4))
                            .frame(height: 3)
                        Capsule()
                            .fill(progressColor)
                            .frame(width: geo.size.width * progressFraction, height: 3)
                            .animation(.olasStandard, value: progressFraction)
                    }
                }
                .frame(height: 3)
            }

            Spacer(minLength: 0)

            // Done checkmark or spinner
            Group {
                if case .done = upload.step {
                    Button {
                        UploadQueue.shared.clearTerminal()
                    } label: {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Color.olasSuccess)
                            .font(.system(size: 18))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Dismiss")
                } else if case .error = upload.step {
                    Button {
                        UploadQueue.shared.clearTerminal()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(Color.olasDestructive)
                            .font(.system(size: 18))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Dismiss")
                } else {
                    ProgressView()
                        .tint(Color.olasText2)
                        .scaleEffect(0.8)
                }
            }
            .frame(width: 24)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.08), lineWidth: 0.5)
                }
        }
        .padding(.horizontal, 12)
        .opacity(appeared ? 1 : 0)
        .offset(y: appeared ? 0 : 12)
        .onAppear {
            withAnimation(.spring(response: 0.38, dampingFraction: 0.78)) { appeared = true }
        }
    }

    private var statusLabel: String {
        switch upload.step {
        case .idle:             return "Preparing…"
        case .encoding:         return "Preparing photo…"
        case .uploading:        return "Uploading…"
        case .publishing:       return "Publishing…"
        case .done:             return "Posted!"
        case .error(let msg):   return msg
        }
    }

    private var progressFraction: CGFloat {
        switch upload.step {
        case .idle:             return 0
        case .encoding:         return 0.1
        case .uploading(let p): return 0.1 + 0.7 * CGFloat(p)
        case .publishing:       return 0.85
        case .done:             return 1.0
        case .error:            return 1.0
        }
    }

    private var progressColor: Color {
        switch upload.step {
        case .error:  return Color.olasDestructive
        case .done:   return Color.olasSuccess
        default:      return Color.olasBlue
        }
    }
}
