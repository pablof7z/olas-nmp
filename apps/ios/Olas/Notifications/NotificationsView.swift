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

// MARK: - ViewModel

@Observable @MainActor
final class NotificationsViewModel {
    /// Grouped + deduped rows produced by Rust.
    var groupedNotifications: [OlasGroupedNotification] = []
    var isLoading = false

    // Raw per-notification payload JSON strings accumulated from the event stream.
    private var rawPayloads: [String] = []
    // IDs of notifications already accumulated (dedup).
    private var seenIds: Set<String> = []

    func start() {
        isLoading = true
        NMPBridge.shared.addEventHandler { [weak self] json in
            self?.handleEvent(json)
        }
    }

    private func handleEvent(_ json: String) {
        isLoading = false
        // Rust decodes & validates the notification; ignore unrecognised event kinds.
        guard let payload = NMPBridge.shared.notificationPayloadJSON(json) else { return }
        // Deduplicate by notification ID.
        guard let id = extractId(from: payload), seenIds.insert(id).inserted else { return }
        rawPayloads.append(payload)
        rebuildGroups()
    }

    private func extractId(from payloadJSON: String) -> String? {
        guard let data = payloadJSON.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let id = obj["id"] as? String else { return nil }
        return id
    }

    private func rebuildGroups() {
        guard !rawPayloads.isEmpty else { return }
        let joined = rawPayloads.joined(separator: ",")
        let arrayJSON = "[\(joined)]"
        groupedNotifications = NMPBridge.shared.groupNotificationsJSON(arrayJSON) ?? []
    }

    func filtered(by tab: NotificationsTab) -> [OlasGroupedNotification] {
        switch tab {
        case .all: return groupedNotifications
        case .mentions: return groupedNotifications.filter {
            $0.kind == "comment" || $0.kind == "mention"
        }
        case .zaps: return groupedNotifications.filter { $0.kind == "zap" }
        }
    }

    // MARK: - Time sections (presentation-only, native responsibility)

    struct NotifSection: Identifiable {
        let title: String
        let items: [OlasGroupedNotification]
        var id: String { title }
    }

    func sections(for tab: NotificationsTab) -> [NotifSection] {
        let items = filtered(by: tab)
        guard !items.isEmpty else { return [] }

        let cal = Calendar.current
        let now = Date()
        let startOfToday = cal.startOfDay(for: now)
        let startOfWeek = cal.date(byAdding: .day, value: -7, to: startOfToday) ?? startOfToday

        var today: [OlasGroupedNotification] = []
        var week: [OlasGroupedNotification] = []
        var earlier: [OlasGroupedNotification] = []

        for item in items {
            let date = Date(timeIntervalSince1970: TimeInterval(item.latestTs))
            if date >= startOfToday { today.append(item) }
            else if date >= startOfWeek { week.append(item) }
            else { earlier.append(item) }
        }

        var result: [NotifSection] = []
        if !today.isEmpty   { result.append(NotifSection(title: "Today", items: today)) }
        if !week.isEmpty    { result.append(NotifSection(title: "This Week", items: week)) }
        if !earlier.isEmpty { result.append(NotifSection(title: "Earlier", items: earlier)) }
        return result
    }
}

// MARK: - View

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

                if vm.isLoading && vm.groupedNotifications.isEmpty {
                    Spacer()
                    ProgressView().tint(Color.olasText2)
                    Spacer()
                } else if vm.filtered(by: selectedTab).isEmpty {
                    emptyState
                } else {
                    sectionedList
                }
            }
            .background(Color.olasBackground)
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        ForEach(NotificationsTab.allCases, id: \.self) { tab in
                            Button {
                                withAnimation(.olasStandard) { selectedTab = tab }
                            } label: {
                                if selectedTab == tab {
                                    Label(tab.rawValue, systemImage: "checkmark")
                                } else {
                                    Text(tab.rawValue)
                                }
                            }
                        }
                    } label: {
                        Image(systemName: selectedTab == .all
                              ? "line.3.horizontal.decrease.circle"
                              : "line.3.horizontal.decrease.circle.fill")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(selectedTab == .all ? Color.olasText2 : Color.olasText1)
                    }
                }
            }
        }
        .onAppear { vm.start() }
    }

    private var sectionedList: some View {
        List {
            ForEach(vm.sections(for: selectedTab)) { section in
                Section {
                    ForEach(section.items) { notification in
                        NotificationItemView(notification: notification)
                            .listRowBackground(Color.olasBackground)
                            .listRowInsets(EdgeInsets())
                            .listRowSeparatorTint(Color.olasBorder)
                    }
                } header: {
                    Text(section.title)
                        .font(OlasFont.caption())
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.olasText3)
                        .padding(.horizontal, OlasSpacing.md)
                        .padding(.top, OlasSpacing.xs)
                        .listRowInsets(EdgeInsets())
                        .background(Color.olasBackground)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
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
