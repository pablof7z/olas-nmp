import SwiftUI

private let wotSettingsNote =
    "Network uses your local trust graph. Close is stricter; Open still hides accounts you mute."

enum WoTPreset: String, CaseIterable, Identifiable {
    case close = "Close"
    case balanced = "Balanced"
    case open = "Open"

    var id: String { rawValue }

    var storageValue: String {
        rawValue.lowercased()
    }

    var description: String {
        switch self {
        case .close: return "Just the people you follow and those they follow closely."
        case .balanced: return "Your broader network, friends of friends."
        case .open: return "Everyone. More to discover, less filtered."
        }
    }

    var icon: String {
        switch self {
        case .close: return "person.2.fill"
        case .balanced: return "shield.lefthalf.filled"
        case .open: return "globe"
        }
    }

    var accentColor: Color {
        switch self {
        case .close: return Color.olasSuccess
        case .balanced: return Color.olasBlue
        case .open: return Color.olasZap
        }
    }
}

struct WoTSettingsView: View {
    @AppStorage("wotPreset") private var selectedPreset = WoTPreset.balanced.storageValue

    var body: some View {
        ScrollView {
            VStack(spacing: OlasSpacing.md) {
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

                VStack(spacing: OlasSpacing.sm) {
                    ForEach(WoTPreset.allCases) { preset in
                        presetCard(preset)
                    }
                }
                .padding(.horizontal, OlasSpacing.md)

                HStack(alignment: .top, spacing: OlasSpacing.sm) {
                    Image(systemName: "info.circle")
                        .foregroundStyle(Color.olasBlue)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Trust filtering is active")
                            .font(OlasFont.caption())
                            .fontWeight(.semibold)
                            .foregroundStyle(Color.olasText1)
                        Text(wotSettingsNote)
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)
                    }
                }
                .padding(OlasSpacing.md)
                .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, OlasSpacing.md)

                Spacer(minLength: 40)
            }
        }
        .background(Color.olasBackground)
        .navigationTitle("Content & Filtering")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func presetCard(_ option: WoTPreset) -> some View {
        let isSelected = option.storageValue == selectedPreset.lowercased()
        return Button {
            selectedPreset = option.storageValue
        } label: {
            HStack(spacing: OlasSpacing.md) {
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

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? option.accentColor : Color.olasBorder)
                    .font(.system(size: 22))
            }
            .padding(OlasSpacing.md)
            .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? option.accentColor : Color.olasBorder, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}
