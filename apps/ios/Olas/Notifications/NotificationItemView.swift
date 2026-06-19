import SwiftUI

struct NotificationItemView: View {
    let notification: OlasNotification

    var body: some View {
        HStack(alignment: .top, spacing: OlasSpacing.sm) {
            // Avatar with type badge
            ZStack(alignment: .bottomTrailing) {
                AsyncImage(url: URL(string: notification.actorAvatar ?? "")) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Circle().fill(Color.olasSurface2)
                }
                .frame(width: 36, height: 36)
                .clipShape(Circle())

                typeBadge
            }

            VStack(alignment: .leading, spacing: OlasSpacing.xxs) {
                notificationText
                    .font(OlasFont.feedCaption())
                    .foregroundStyle(Color.olasText1)
                    .lineLimit(2)

                Text(notification.createdAt.relativeTimeString)
                    .font(OlasFont.feedTimestamp())
                    .foregroundStyle(Color.olasText3)
            }

            Spacer()

            // Post thumbnail
            if let thumbnail = notification.postThumbnail {
                AsyncImage(url: URL(string: thumbnail)) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Rectangle().fill(Color.olasSurface2)
                }
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 6))
            }
        }
        .padding(.horizontal, OlasSpacing.md)
        .padding(.vertical, OlasSpacing.sm)
        .background(Color.olasBackground)
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
        switch notification.type {
        case .reaction:  return ("heart.fill", Color.olasHeart)
        case .comment:   return ("bubble.right.fill", Color.olasBlue)
        case .mention:   return ("at", Color.olasBlue)
        case .follow:    return ("person.fill.badge.plus", Color.olasSuccess)
        case .repost:    return ("arrow.2.squarepath", Color.olasText2)
        case .zap:       return ("bolt.fill", Color.olasZap)
        }
    }

    private var notificationText: some View {
        let actor = notification.actorName ?? String(notification.actorPubkey.prefix(8))
        let grouped = notification.groupCount > 1 ? " and \(notification.groupCount - 1) others" : ""
        let groupedText = "\(actor)\(grouped)"

        return switch notification.type {
        case .reaction:  Text("**\(groupedText)** reacted to your photo")
        case .comment:   Text("**\(groupedText)** commented on your photo")
        case .mention:   Text("**\(groupedText)** mentioned you")
        case .follow:    Text("**\(groupedText)** followed you")
        case .repost:    Text("**\(groupedText)** reposted your photo")
        case .zap(let amount): Text("**\(groupedText)** zapped ⚡ \(amount) sats")
        }
    }
}
