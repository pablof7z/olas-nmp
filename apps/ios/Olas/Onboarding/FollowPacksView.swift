import SwiftUI

struct FollowPacksView: View {
    @Bindable var vm: OnboardingViewModel
    private let packs = FollowPack.defaults

    private var selectedCount: Int { vm.selectedPackIds.count }
    private var creatorCount: Int {
        packs
            .filter { vm.selectedPackIds.contains($0.id) }
            .reduce(0) { $0 + $1.count }
    }

    var body: some View {
        VStack(spacing: 0) {
            Text("Follow some people")
                .font(OlasFont.title2())
                .foregroundStyle(Color.olasText1)
                .padding(.top, 56)

            Text("Get a better feed right away")
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText2)
                .padding(.top, OlasSpacing.xs)

            ScrollView {
                VStack(spacing: OlasSpacing.sm) {
                    ForEach(packs) { pack in
                        FollowPackCard(
                            pack: pack,
                            isSelected: vm.selectedPackIds.contains(pack.id),
                            onToggle: {
                                if vm.selectedPackIds.contains(pack.id) {
                                    vm.selectedPackIds.remove(pack.id)
                                } else {
                                    vm.selectedPackIds.insert(pack.id)
                                }
                            }
                        )
                    }
                }
                .padding(.horizontal, OlasSpacing.md)
                .padding(.top, OlasSpacing.xl)
                .padding(.bottom, 120)
            }
        }

        // Bottom bar
        VStack(spacing: 0) {
            Rectangle().fill(Color.olasBorder).frame(height: 0.5)

            VStack(spacing: OlasSpacing.sm) {
                if selectedCount > 0 {
                    Text("\(selectedCount) pack\(selectedCount == 1 ? "" : "s") · \(creatorCount) creators")
                        .font(OlasFont.subheadline())
                        .foregroundStyle(Color.olasText2)
                }

                Button {
                    followSelectedPacks()
                    vm.advance(to: .mediaServer)
                } label: {
                    Text(selectedCount > 0 ? "Continue" : "Skip")
                        .font(OlasFont.headline())
                        .foregroundStyle(Color.olasBackground)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(OlasPressedButtonStyle())
            }
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.vertical, OlasSpacing.md)
            .padding(.bottom, 16)
            .background(Color.olasBackground)
        }
    }

    private func followSelectedPacks() {
        guard !vm.selectedPackIds.isEmpty else { return }
        let selectedPacks = packs.filter { vm.selectedPackIds.contains($0.id) }
        for pack in selectedPacks {
            for pubkey in pack.previewPubkeys {
                let json = "{\"pubkey\":\"\(pubkey)\"}"
                _ = NMPBridge.shared.dispatchAction(namespace: "nmp.follow", json: json)
            }
        }
    }
}

struct FollowPackCard: View {
    let pack: FollowPack
    let isSelected: Bool
    let onToggle: () -> Void

    private let previewAvatars = (1...6).map { "https://i.pravatar.cc/150?img=\($0 + 30)" }

    var body: some View {
        Button(action: onToggle) {
            VStack(alignment: .leading, spacing: 0) {
                // Accent bar
                Rectangle()
                    .fill(Color(hex: pack.accentColor))
                    .frame(height: 3)

                VStack(alignment: .leading, spacing: OlasSpacing.sm) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pack.name)
                                .font(OlasFont.headline())
                                .foregroundStyle(Color.olasText1)

                            Text(pack.description)
                                .font(OlasFont.caption())
                                .foregroundStyle(Color.olasText2)
                                .lineLimit(2)
                        }

                        Spacer()

                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 22))
                            .foregroundStyle(isSelected ? Color(hex: pack.accentColor) : Color.olasBorder)
                    }

                    // Preview avatars
                    HStack(spacing: -10) {
                        ForEach(previewAvatars.prefix(6), id: \.self) { url in
                            AsyncImage(url: URL(string: url)) { img in
                                img.resizable().scaledToFill()
                            } placeholder: {
                                Circle().fill(Color.olasSurface2)
                            }
                            .frame(width: 28, height: 28)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.olasSurface, lineWidth: 1.5))
                        }

                        Spacer()

                        Text("\(pack.count) creators")
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)
                    }
                }
                .padding(OlasSpacing.md)
            }
            .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color(hex: pack.accentColor) : Color.olasBorder, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
        .animation(.olasStandard, value: isSelected)
    }
}
