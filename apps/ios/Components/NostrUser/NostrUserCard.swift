import SwiftUI

/// Compact author header: avatar, display name, and optional NIP-05 badge.
///
/// The most common pattern in note feeds and thread views. Tap routes
/// through `onTap` so this component can be placed in any navigation stack.
///
/// Depends on `swiftui/user-avatar`, `swiftui/user-name`,
/// `swiftui/user-nip05`.
public struct NostrUserCard: View {
    public let profile: ProfileWire
    public var avatarSize: CGFloat
    public var onTap: ((String) -> Void)?

    public init(
        profile: ProfileWire,
        avatarSize: CGFloat = 40,
        onTap: ((String) -> Void)? = nil
    ) {
        self.profile = profile
        self.avatarSize = avatarSize
        self.onTap = onTap
    }

    public var body: some View {
        HStack(spacing: 10) {
            NostrAvatar(profile: profile, size: avatarSize)

            VStack(alignment: .leading, spacing: 2) {
                NostrProfileName(profile: profile)

                if let badge = NostrNip05Badge(profile: profile) {
                    badge
                }
            }

            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .onTapGesture { onTap?(profile.pubkey) }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(profile.display), profile")
        .accessibilityAddTraits(.isButton)
    }
}
