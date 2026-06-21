import SwiftUI

// Follow packs are sourced entirely from the Rust onboarding snapshot
// (`olas_follow_packs_snapshot_json`). Native only renders the wire struct and
// forwards opaque pack ids back; it never inspects protocol details.

struct FollowPacksView: View {
    @Bindable var vm: OnboardingViewModel

    private var summary: (packs: Int, people: Int) { vm.selectionSummary }
    private var hasSelection: Bool { !vm.selectedPackIds.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            header

            switch vm.followPacks?.state {
            case "empty_offline":
                offlineView
            case "ready":
                packList
            default:
                skeletonView   // "loading" or not-yet-loaded
            }
        }
        .onAppear { vm.startPackDiscovery() }
        .onDisappear { vm.stopPackDiscovery() }
        .safeAreaInset(edge: .bottom) { footer }
    }

    // MARK: - Header

    private var header: some View {
        VStack(spacing: OlasSpacing.xs) {
            Text("Follow some people")
                .font(OlasFont.title2())
                .foregroundStyle(Color.olasText1)
                .padding(.top, 56)

            Text("Get a better feed right away")
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText2)
        }
    }

    // MARK: - Pack list (one continuous mixed list, no section dividers)

    private var packList: some View {
        ScrollView {
            VStack(spacing: OlasSpacing.sm) {
                ForEach(vm.followPacks?.packs ?? []) { pack in
                    FollowPackCard(
                        pack: pack,
                        isSelected: vm.selectedPackIds.contains(pack.id),
                        onToggle: { vm.togglePack(pack.id) }
                    )
                }
            }
            .padding(.horizontal, OlasSpacing.md)
            .padding(.top, OlasSpacing.xl)
            .padding(.bottom, OlasSpacing.lg)
        }
    }

    // MARK: - Loading skeleton

    private var skeletonView: some View {
        ScrollView {
            VStack(spacing: OlasSpacing.sm) {
                ForEach(0..<4, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Color.olasSurface)
                        .frame(height: 132)
                        .redacted(reason: .placeholder)
                }
            }
            .padding(.horizontal, OlasSpacing.md)
            .padding(.top, OlasSpacing.xl)
        }
    }

    // MARK: - Offline

    private var offlineView: some View {
        VStack(spacing: OlasSpacing.md) {
            Spacer()
            Image(systemName: "wifi.slash")
                .font(.system(size: 44, weight: .thin))
                .foregroundStyle(Color.olasText3)
            Text("We couldn't load suggestions right now")
                .font(OlasFont.body())
                .foregroundStyle(Color.olasText2)
                .multilineTextAlignment(.center)
            Button("Try again") { vm.startPackDiscovery() }
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasBlue)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, OlasSpacing.xl)
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.olasBorder).frame(height: 0.5)

            VStack(spacing: OlasSpacing.sm) {
                if hasSelection {
                    Text("\(summary.packs) pack\(summary.packs == 1 ? "" : "s") · \(summary.people) \(summary.people == 1 ? "person" : "people")")
                        .font(OlasFont.subheadline())
                        .foregroundStyle(Color.olasText2)
                }

                Button {
                    vm.applySelectedPacks()
                } label: {
                    Text(hasSelection ? "Continue" : "Skip")
                        .font(OlasFont.headline())
                        .foregroundStyle(Color.olasBackground)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Color.olasText1, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(OlasPressedButtonStyle())
            }
            .padding(.horizontal, OlasSpacing.xl)
            .padding(.top, OlasSpacing.md)
            .padding(.bottom, OlasSpacing.xs)
            .background(Color.olasBackground)
        }
    }
}

// MARK: - Card

struct FollowPackCard: View {
    let pack: FollowPack
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            VStack(alignment: .leading, spacing: 0) {
                coverImage
                details
            }
            .background(Color.olasSurface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isSelected ? Color.olasText1 : Color.olasBorder,
                            lineWidth: isSelected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .animation(.olasStandard, value: isSelected)
    }

    private var coverImage: some View {
        ZStack(alignment: .topLeading) {
            Group {
                if let urlStr = pack.coverImageUrl, let url = URL(string: urlStr) {
                    CachedImage(url: url,
                                loading: { Color.olasSurface2 },
                                failure: { Color.olasSurface2 })
                } else {
                    Color.olasSurface2
                }
            }
            .frame(height: 110)
            .frame(maxWidth: .infinity)
            .clipped()

            if pack.featured {
                Text("Featured")
                    .font(OlasFont.captionSmall().weight(.semibold))
                    .foregroundStyle(Color.olasText1)
                    .padding(.horizontal, OlasSpacing.xs)
                    .padding(.vertical, 4)
                    .background(.ultraThinMaterial, in: Capsule())
                    .padding(OlasSpacing.xs)
            }
        }
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: OlasSpacing.xs) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(pack.title)
                        .font(OlasFont.headline())
                        .foregroundStyle(Color.olasText1)
                        .lineLimit(1)
                    if let description = pack.description, !description.isEmpty {
                        Text(description)
                            .font(OlasFont.caption())
                            .foregroundStyle(Color.olasText2)
                            .lineLimit(2)
                    }
                }
                Spacer()
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 24))
                    .foregroundStyle(isSelected ? Color.olasText1 : Color.olasBorder)
            }

            HStack(spacing: -10) {
                ForEach(Array(pack.previewAvatars.prefix(6).enumerated()), id: \.offset) { _, avatar in
                    PackAvatar(avatar: avatar)
                        .overlay(Circle().stroke(Color.olasSurface, lineWidth: 1.5))
                }
                Spacer()
                Text("\(pack.memberCount) \(pack.memberCount == 1 ? "person" : "people")")
                    .font(OlasFont.caption())
                    .foregroundStyle(Color.olasText2)
            }
        }
        .padding(OlasSpacing.md)
    }
}

// MARK: - Avatar (rendered from wire image_url / display_name; no pubkey)

private struct PackAvatar: View {
    let avatar: FollowPackAvatar
    private let size: CGFloat = 28

    var body: some View {
        Group {
            if let urlStr = avatar.imageUrl, let url = URL(string: urlStr) {
                CachedImage(url: url, loading: { placeholder }, failure: { placeholder })
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var placeholder: some View {
        ZStack {
            Circle().fill(Color.olasSurface2)
            Text(initial)
                .font(.system(size: size * 0.4, weight: .semibold))
                .foregroundStyle(Color.olasText2)
        }
    }

    private var initial: String {
        guard let name = avatar.displayName, let first = name.first else { return "" }
        return String(first).uppercased()
    }
}
