import SwiftUI

struct MediaServerView: View {
    @Bindable var vm: OnboardingViewModel

    private struct ServerOption: Identifiable {
        let id: String
        let name: String
        let description: String
        let url: String
        let isFeatured: Bool
    }

    private let options: [ServerOption] = [
        ServerOption(
            id: "primal",
            name: "Olas Network",
            description: "Fast and free. Powered by Primal's Blossom infrastructure.",
            url: "https://blossom.primal.net",
            isFeatured: true
        ),
            ServerOption(
                id: "satellite",
                name: "Satellite.earth",
                description: "Community-run media server for open social photos.",
                url: "https://cdn.satellite.earth",
                isFeatured: false
            ),
            ServerOption(
                id: "nostrcheck",
                name: "Privacy Host",
                description: "Privacy-focused media hosting.",
                url: "https://nostrcheck.me",
                isFeatured: false
            )
    ]

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: OlasSpacing.xs) {
                Text("Where to post")
                    .font(OlasFont.title2())
                    .foregroundStyle(Color.olasText1)
                    .padding(.top, 56)

                Text("Your photos are uploaded to a media server. You can change this anytime.")
                    .font(OlasFont.subheadline())
                    .foregroundStyle(Color.olasText2)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, OlasSpacing.xxl)
                    .padding(.top, OlasSpacing.xs)
            }

            VStack(spacing: OlasSpacing.sm) {
                ForEach(options) { option in
                    serverCard(option)
                }
            }
            .padding(.horizontal, OlasSpacing.md)
            .padding(.top, OlasSpacing.xxl)

            Spacer()

            Button {
                UserDefaults.standard.set(vm.mediaServerURL, forKey: "primaryBlossomServer")
                vm.advance(to: .complete)
            } label: {
                Text("Continue")
                    .font(OlasFont.headline())
                    .foregroundStyle(Color.olasBackground)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(OlasPressedButtonStyle())
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.bottom, 48)
        }
        .background(Color.olasBackground)
    }

    private func serverCard(_ option: ServerOption) -> some View {
        let isSelected = vm.mediaServerURL == option.url
        return Button {
            vm.mediaServerURL = option.url
        } label: {
            HStack(spacing: OlasSpacing.md) {
                VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                    HStack(spacing: OlasSpacing.xs) {
                        Text(option.name)
                            .font(OlasFont.headline())
                            .foregroundStyle(Color.olasText1)

                        if option.isFeatured {
                            Text("RECOMMENDED")
                                .font(OlasFont.captionSmall())
                                .foregroundStyle(Color.olasBlue)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.olasBlue.opacity(0.15), in: Capsule())
                        }
                    }

                    Text(option.description)
                        .font(OlasFont.caption())
                        .foregroundStyle(Color.olasText2)
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(isSelected ? Color.olasBlue : Color.olasBorder)
            }
            .padding(OlasSpacing.md)
            .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.olasBlue : Color.olasBorder, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
        .animation(.olasStandard, value: isSelected)
    }
}
