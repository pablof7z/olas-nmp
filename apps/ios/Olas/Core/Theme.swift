import SwiftUI

// MARK: - Color Extensions

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r, g, b, a: UInt64
        switch hex.count {
        case 6:
            (r, g, b, a) = ((int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF, 255)
        case 8:
            (r, g, b, a) = ((int >> 24) & 0xFF, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (r, g, b, a) = (0, 0, 0, 255)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }

    // MARK: Dark mode design tokens
    static let olasBackground  = Color(hex: "#0A0A0A")
    static let olasSurface     = Color(hex: "#161616")
    static let olasSurface2    = Color(hex: "#1E1E1E")
    static let olasBorder      = Color(hex: "#2E2E2E")
    static let olasBorderSubtle = Color(hex: "#1A1A1A")
    static let olasText1       = Color(hex: "#F5F5F5")
    static let olasText2       = Color(hex: "#999999")
    static let olasText3       = Color(hex: "#555555")
    static let olasZap         = Color(hex: "#FBB131")
    static let olasHeart       = Color(hex: "#FF375F")
    static let olasSuccess     = Color(hex: "#34C759")
    static let olasBlue        = Color(hex: "#0A84FF")
    static let olasPurple      = Color(hex: "#BF5AF2")
    static let olasDestructive = Color(hex: "#FF5B54")
}

// MARK: - Animation Extensions

extension Animation {
    static let olasStandard = Animation.spring(response: 0.32, dampingFraction: 0.72)
    static let olasBouncy   = Animation.spring(response: 0.25, dampingFraction: 0.65)
    static let olasPressed  = Animation.spring(response: 0.25, dampingFraction: 0.72)
}

// MARK: - Typography

struct OlasFont {
    static func largeTitle() -> Font { .system(size: 34, weight: .regular, design: .default) }
    static func title1() -> Font { .system(size: 28, weight: .semibold) }
    static func title2() -> Font { .system(size: 22, weight: .semibold) }
    static func headline() -> Font { .system(size: 17, weight: .semibold) }
    static func body() -> Font { .system(size: 17, weight: .regular) }
    static func callout() -> Font { .system(size: 16, weight: .regular) }
    static func subheadline() -> Font { .system(size: 15, weight: .regular) }
    static func caption() -> Font { .system(size: 13, weight: .regular) }
    static func captionSmall() -> Font { .system(size: 11, weight: .regular) }
    static func footnote() -> Font { .system(size: 13, weight: .regular) }

    // Feed-specific
    static func feedUsername() -> Font { .system(size: 14, weight: .semibold) }
    static func feedCaption() -> Font { .system(size: 14, weight: .regular) }
    static func feedTimestamp() -> Font { .system(size: 13, weight: .regular) }
    static func feedReactionCount() -> Font { .system(size: 13, weight: .semibold).monospacedDigit() }
    static func zapAmount() -> Font { .system(size: 13, weight: .semibold).monospacedDigit() }
    static func wordmark() -> Font { .system(size: 52, weight: .black) }
}

// MARK: - Spacing

enum OlasSpacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat  = 8
    static let sm: CGFloat  = 12
    static let md: CGFloat  = 16
    static let lg: CGFloat  = 20
    static let xl: CGFloat  = 24
    static let xxl: CGFloat = 32
    static let xxxl: CGFloat = 40
}

// MARK: - Reusable Button Style

struct OlasPressedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
            .opacity(configuration.isPressed ? 0.85 : 1.0)
            .animation(.olasPressed, value: configuration.isPressed)
    }
}

// MARK: - Relative Time Formatter

extension Int64 {
    var relativeTimeString: String {
        let now = Int64(Date().timeIntervalSince1970)
        let diff = now - self
        switch diff {
        case ..<60: return "just now"
        case 60..<3600: return "\(diff / 60)m"
        case 3600..<86400: return "\(diff / 3600)h"
        case 86400..<604800: return "\(diff / 86400)d"
        default:
            let date = Date(timeIntervalSince1970: TimeInterval(self))
            let formatter = DateFormatter()
            formatter.dateFormat = "MMM d"
            return formatter.string(from: date)
        }
    }
}
