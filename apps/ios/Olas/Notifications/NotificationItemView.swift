import SwiftUI

struct NotificationItemView: View {
    let notification: OlasGroupedNotification

    // Avatar stack constants
    private static let avatarSize: CGFloat = 30
    private static let avatarOverlap: CGFloat = 18 // x-offset per subsequent avatar

    var body: some View {
        HStack(alignment: .top, spacing: OlasSpacing.sm) {
            // Stacked actor avatars with type badge on the last one
            ZStack(alignment: .bottomTrailing) {
                stackedAvatars
                typeBadge
            }

            VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                notificationText
                    .font(OlasFont.feedCaption())
                    .foregroundStyle(Color.olasText1)
                    .lineLimit(2)

                Text(notification.latestTs.relativeTimeString)
                    .font(OlasFont.feedTimestamp())
                    .foregroundStyle(Color.olasText3)
            }

            Spacer()
        }
        .padding(.horizontal, OlasSpacing.md)
        .padding(.vertical, OlasSpacing.sm)
        .background(Color.olasBackground)
    }

    // MARK: - Stacked avatars (up to 3, overlapping left-to-right)

    @ViewBuilder
    private var stackedAvatars: some View {
        let actors = Array(notification.actorPubkeys.prefix(3))
        let totalWidth = Self.avatarSize + CGFloat(max(0, actors.count - 1)) * Self.avatarOverlap
        ZStack(alignment: .leading) {
            ForEach(Array(actors.enumerated()), id: \.offset) { i, pubkey in
                NostrAvatar(pubkey: pubkey, size: Self.avatarSize)
                    .offset(x: CGFloat(i) * Self.avatarOverlap)
                    .zIndex(Double(actors.count - i)) // first actor on top
            }
        }
        .frame(width: totalWidth, height: Self.avatarSize)
    }

    @ViewBuilder
    private var typeBadge: some View {
        let (icon, color) = badgeInfo
        Image(systemName: icon)
            .font(.system(size: 9, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 16, height: 16)
            .background(color, in: Circle())
            .offset(x: 2, y: 2)
    }

    private var badgeInfo: (String, Color) {
        switch notification.kind {
        case "reaction": return ("heart.fill", Color.olasHeart)
        case "comment":  return ("bubble.right.fill", Color.olasBlue)
        case "mention":  return ("at", Color.olasBlue)
        case "follow":   return ("person.fill.badge.plus", Color.olasSuccess)
        case "repost":   return ("arrow.2.squarepath", Color.olasText2)
        case "zap":      return ("bolt.fill", Color.olasZap)
        default:         return ("bell.fill", Color.olasText2)
        }
    }

    // MARK: - Actor label: "alice, bob +N others"

    private var actorLabel: String {
        let cache = NMPBridge.shared.profileCache
        let names = notification.actorPubkeys.prefix(2).map { pubkey in
            cache[pubkey]?.displayName ?? String(pubkey.prefix(8))
        }
        let others = notification.count - names.count
        let base = names.joined(separator: ", ")
        return others > 0 ? "\(base) +\(others) others" : base
    }

    @ViewBuilder
    private var notificationText: some View {
        let actor = actorLabel
        switch notification.kind {
        case "reaction": Text("**\(actor)** reacted to your photo")
        case "comment":  Text("**\(actor)** commented on your photo")
        case "mention":  Text("**\(actor)** mentioned you")
        case "follow":   Text("**\(actor)** followed you")
        case "repost":   Text("**\(actor)** reposted your photo")
        case "zap":
            let sats = notification.zapSats ?? 0
            if sats > 0 { Text("**\(actor)** zapped ⚡ \(sats) sats") }
            else { Text("**\(actor)** zapped your photo") }
        default: Text("**\(actor)** interacted with you")
        }
    }
}
