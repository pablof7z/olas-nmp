import SwiftUI

enum WoTPreset: String, CaseIterable, Identifiable {
    case close = "Close"
    case balanced = "Balanced"
    case open = "Open"

    var id: String { rawValue }

    var description: String {
        switch self {
        case .close:    return "Just the people you follow and those they follow closely."
        case .balanced: return "Your broader network — friends of friends."
        case .open:     return "Everyone. More to discover, less filtered."
        }
    }

    var icon: String {
        switch self {
        case .close:    return "person.2.fill"
        case .balanced: return "shield.lefthalf.filled"
        case .open:     return "globe"
        }
    }

    var accentColor: Color {
        switch self {
        case .close:    return Color.olasSuccess
        case .balanced: return Color.olasBlue
        case .open:     return Color.olasZap
        }
    }
}

struct WoTSettingsView: View {
    // No @AppStorage — WoT has no Rust backing yet. All controls are read-only.
    // Hardcoded default preset shown for illustration only.
    private let defaultPreset: WoTPreset = .balanced

    var body: some View {
        ScrollView {
            VStack(spacing: OlasSpacing.md) {
                // Header
                VStack(spacing: OlasSpacing.xxs) {
                    Text("Who shows up in your feed")
                        .font(OlasFont.headline())
                        .foregroundStyle(Color.olasText1)
                    Text("Adjust the balance between discovery and filtering.")
                        .font(OlasFont.subheadline())
                        .foregroundStyle(Color.olasText2)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, OlasSpacing.xl)
                .padding(.horizontal, OlasSpacing.xl)

                // Coming-soon notice
                HStack(spacing: OlasSpacing.sm) {
                    Image(systemName: "clock")
                        .foregroundStyle(Color.olasBlue)
                    Text("Web of Trust filtering is coming soon. Settings will be configurable once the underlying Rust module is available.")
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText2)
                }
                .padding(OlasSpacing.md)
                .background(Color.olasBlue.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, OlasSpacing.md)

                // Preset cards — read-only display
                VStack(spacing: OlasSpacing.sm) {
                    ForEach(WoTPreset.allCases) { presetOption in
                        presetCard(presetOption)
                    }
                }
                .padding(.horizontal, OlasSpacing.md)
                .disabled(true)
                .opacity(0.6)

                Spacer(minLength: 40)
            }
        }
        .background(Color.olasBackground)
        .navigationTitle("Content & Filtering")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func presetCard(_ option: WoTPreset) -> some View {
        let isDefault = option == defaultPreset
        return HStack(spacing: OlasSpacing.md) {
            Image(systemName: option.icon)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(option.accentColor)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(option.rawValue)
                    .font(OlasFont.headline())
                    .foregroundStyle(Color.olasText1)
                Text(option.description)
                    .font(OlasFont.caption())
                    .foregroundStyle(Color.olasText2)
            }

            Spacer()

            Image(systemName: isDefault ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(isDefault ? option.accentColor : Color.olasBorder)
                .font(.system(size: 22))
        }
        .padding(OlasSpacing.md)
        .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isDefault ? option.accentColor : Color.olasBorder, lineWidth: 1.5)
        )
    }
}
