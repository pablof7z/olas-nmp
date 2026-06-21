import UIKit
import AVFoundation

// MARK: - Haptics

/// Centralised haptic helper for Olas.
///
/// All methods create a fresh feedback generator, call `prepare()` immediately
/// before triggering — keeping the Taptic Engine warm without long-lived state.
///
/// The OS gates execution automatically:
/// - `UIFeedbackGenerator` respects System Preferences → Sounds & Haptics → Vibration
///   and the Low Power Mode flag. No extra guarding is needed here.
/// - Calling these from the main actor (SwiftUI callbacks, UploadQueue) is always safe.
@MainActor
enum OlasHaptics {

    // MARK: Impact

    /// Light click — use for like/heart tap.
    static func impactLight() {
        let g = UIImpactFeedbackGenerator(style: .light)
        g.prepare()
        g.impactOccurred()
    }

    /// Soft thud — use for revealing queued content (new-posts pill tap).
    static func impactSoft() {
        let g = UIImpactFeedbackGenerator(style: .soft)
        g.prepare()
        g.impactOccurred()
    }

    /// Rigid click — use for filter carousel per-cell selection tick.
    static func impactRigid() {
        let g = UIImpactFeedbackGenerator(style: .rigid)
        g.prepare()
        g.impactOccurred()
    }

    // MARK: Notification

    /// Success triple-tap — use for zap sent and post published.
    static func notificationSuccess() {
        let g = UINotificationFeedbackGenerator()
        g.prepare()
        g.notificationOccurred(.success)
    }

    // MARK: Selection

    /// Selection tick — use for feed mode switch and new-posts pill appear.
    static func selectionChanged() {
        let g = UISelectionFeedbackGenerator()
        g.prepare()
        g.selectionChanged()
    }
}

// MARK: - Sound

/// Opt-in sound effects for Olas.
///
/// Gated by the `"soundEffectsEnabled"` UserDefaults key (default `false`) which mirrors
/// the in-app toggle in Settings → Appearance → Sound Effects.
///
/// Uses `AVAudioSession.Category.ambient` so effects:
/// - respect the hardware silent/ringer switch,
/// - do not interrupt background music or podcasts.
///
/// Audio assets ("ShutterSoft.caf", "ZapChime.caf") are **optional**. If an asset is
/// absent from the app bundle the call is a silent no-op. Real asset files can be added
/// later without changing any call-site code.
@MainActor
enum OlasSound {

    static var isEnabled: Bool {
        UserDefaults.standard.bool(forKey: "soundEffectsEnabled")
    }

    /// Soft camera shutter — plays on successful post publish.
    static func shutterSoft() {
        guard isEnabled else { return }
        play(named: "ShutterSoft", ext: "caf")
    }

    /// Warm chime — plays when a zap is sent.
    static func zapChime() {
        guard isEnabled else { return }
        play(named: "ZapChime", ext: "caf")
    }

    // MARK: Private

    // Retained across calls so the player is not deallocated mid-playback.
    private static var player: AVAudioPlayer?

    private static func play(named name: String, ext: String) {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext) else {
            // Asset not yet bundled — silent no-op by design.
            return
        }
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
            let p = try AVAudioPlayer(contentsOf: url)
            p.prepareToPlay()
            p.play()
            player = p // retain until playback ends
        } catch {
            // Silently ignore playback failures (e.g., Simulator without audio support).
        }
    }
}
