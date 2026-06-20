import SwiftUI

// MARK: - WoT gap copy (keep in sync with Android WoTStrings.kt)

/// One-line disclosure shown in the notification list header while the WoT FFI gap is active.
/// Android mirror: `WOT_GAP_NOTIFICATIONS_NOTE` in core/WoTStrings.kt
private let wotGapNotificationsNote =
    "Showing all · Trust scoring updating in the background"

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

    private struct ZapInfo: Decodable { let amount_sats: Int64; let referenced_event_id: String }

    private func handleEvent(_ json: String) {
        guard let data = json.data(using: .utf8),
              let event = try? JSONDecoder().decode(NostrEvent.self, from: data) else { return }
        isLoading = false

        switch event.kind {
        case 7: // reaction
            let n = OlasNotification(
                id: event.id, type: .reaction, actorPubkey: event.author,
                postId: event.tags.first(where: { $0.first == "e" })?[safe: 1],
                createdAt: event.createdAt
            )
            notifications.insert(n, at: 0)
        case 9735: // zap — decoded entirely in Rust
            guard let zapJSON = NMPBridge.shared.decodeZapNotification(json),
                  let zapData = zapJSON.data(using: .utf8),
                  let zap = try? JSONDecoder().decode(ZapInfo.self, from: zapData) else { return }
            let n = OlasNotification(
                id: event.id, type: .zap(zap.amount_sats), actorPubkey: event.author,
                postId: zap.referenced_event_id.isEmpty ? nil : zap.referenced_event_id,
                createdAt: event.createdAt
            )
            notifications.insert(n, at: 0)
        case 1: // mention / comment
            let mentionedId = event.tags.first(where: { $0.first == "e" })?[safe: 1]
            let n = OlasNotification(
                id: event.id, type: .comment, actorPubkey: event.author,
                postId: mentionedId,
                createdAt: event.createdAt
            )
            notifications.insert(n, at: 0)
        case 3: // follow
            let n = OlasNotification(
                id: event.id, type: .follow, actorPubkey: event.author,
                postId: nil,
                createdAt: event.createdAt
            )
            notifications.insert(n, at: 0)
        default:
            break
        }
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
        NavigationStack {
            VStack(spacing: 0) {
                tabBar

                HStack(spacing: OlasSpacing.xs) {
                    Image(systemName: "clock")
                        .font(.system(size: 11, weight: .medium))
                    Text(wotGapNotificationsNote)
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
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {} label: {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.olasText2)
                    }
                }
            }
        }
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
