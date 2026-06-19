import SwiftUI

// MARK: - WoT copy (keep in sync with Android WoTStrings.kt)

/// One-line disclosure shown in the notification list header.
/// Android mirror: `WOT_NOTIFICATIONS_NOTE` in core/WoTStrings.kt
private let wotNotificationsNote =
    "Filtered by your trust settings"

enum NotificationsTab: String, CaseIterable {
    case all = "All"
    case mentions = "Mentions"
    case zaps = "Zaps"
}

@Observable @MainActor
final class NotificationsViewModel {
    var notifications: [OlasNotification] = []
    var isLoading = false

    func start() {
        isLoading = true
        NMPBridge.shared.addEventHandler { [weak self] json in
            self?.handleEvent(json)
        }
    }

    private func handleEvent(_ json: String) {
        guard let notification = NMPBridge.shared.notification(from: json) else { return }
        isLoading = false
        notifications.insert(notification, at: 0)
    }

    func filtered(by tab: NotificationsTab) -> [OlasNotification] {
        switch tab {
        case .all: return notifications
        case .mentions: return notifications.filter {
            if case .comment = $0.type { return true }
            if case .mention = $0.type { return true }
            return false
        }
        case .zaps: return notifications.filter {
            if case .zap = $0.type { return true }
            return false
        }
        }
    }
}

struct NotificationsView: View {
    @State private var vm = NotificationsViewModel()
    @State private var selectedTab: NotificationsTab = .all

    var body: some View {
        VStack(spacing: 0) {
            // Custom header — no NavigationStack
            HStack {
                Spacer()
                Text("Notifications")
                    .font(OlasFont.headline())
                    .foregroundStyle(Color.olasText1)
                Spacer()
                Button {} label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.olasText2)
                }
                .padding(.trailing, OlasSpacing.md)
            }
            .frame(height: 44)
            .background(.ultraThinMaterial)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Color.olasBorder).frame(height: 0.5)
            }

            tabBar

            HStack(spacing: OlasSpacing.xs) {
                Image(systemName: "clock")
                    .font(.system(size: 11, weight: .medium))
                Text(wotNotificationsNote)
                    .font(OlasFont.caption())
            }
            .foregroundStyle(Color.olasText2)
            .padding(.horizontal, OlasSpacing.sm)
            .padding(.vertical, 5)
            .background(Color.olasSurface, in: Capsule())
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, OlasSpacing.md)
            .padding(.vertical, OlasSpacing.xs)
            Divider().overlay(Color.olasBorder)

            if vm.isLoading && vm.notifications.isEmpty {
                Spacer()
                ProgressView().tint(Color.olasText2)
                Spacer()
            } else if vm.filtered(by: selectedTab).isEmpty {
                emptyState
            } else {
                List(vm.filtered(by: selectedTab)) { notification in
                    NotificationItemView(notification: notification)
                        .listRowBackground(Color.olasBackground)
                        .listRowInsets(EdgeInsets())
                        .listRowSeparatorTint(Color.olasBorder)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .background(Color.olasBackground)
        .onAppear { vm.start() }
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(NotificationsTab.allCases, id: \.self) { tab in
                Button {
                    withAnimation(.olasStandard) { selectedTab = tab }
                } label: {
                    Text(tab.rawValue)
                        .font(OlasFont.subheadline())
                        .foregroundStyle(selectedTab == tab ? Color.olasText1 : Color.olasText3)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay(alignment: .bottom) {
                            if selectedTab == tab {
                                Rectangle().fill(Color.olasText1).frame(height: 1)
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.olasBorder).frame(height: 1)
        }
    }

    private var emptyState: some View {
        VStack(spacing: OlasSpacing.sm) {
            Spacer()
            Text("Nothing here yet")
                .font(OlasFont.headline())
                .foregroundStyle(Color.olasText2)
            Text("Interactions will show up here.")
                .font(OlasFont.subheadline())
                .foregroundStyle(Color.olasText3)
            Spacer()
        }
    }
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
