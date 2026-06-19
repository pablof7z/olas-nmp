# Olas Motion & Animation Specification

This directory is the implementation-grade specification for every animation and
transition in Olas. It is written to be handed directly to iOS and Android
engineers. Values are real and prescriptive: where a number is given, ship that
number.

Olas must feel native, premium, and alive — Instagram-quality, not a web wrapper
or a React Native approximation. iOS uses SwiftUI/UIKit; Android uses
Jetpack Compose. The two platforms are held to **behavioral parity**, not
pixel parity: the same physical feel, the same timings, the same haptics, but
each expressed in its platform's idiom (iOS HIG vs Material 3).

## How to read this spec

- **[00-foundations.md](00-foundations.md)** — the shared vocabulary every other
  file references: named spring tokens, named curves, the duration scale, the
  haptic table, and the cross-platform mapping rules. Read this first.
- The numbered topic files below each cover one surface. Each entry specifies
  curve, duration, haptic, and the **principle** behind the choice.

## Topic index

| File | Covers |
|------|--------|
| [00-foundations.md](00-foundations.md) | Spring tokens, curve tokens, duration scale, haptic table, platform mapping |
| [01-feed-scroll.md](01-feed-scroll.md) | Feed scroll physics, overscroll, header parallax, post card appearance |
| [02-image-open-close.md](02-image-open-close.md) | Shared-element grid→fullscreen transition, pinch-dismiss |
| [03-reactions-and-zaps.md](03-reactions-and-zaps.md) | Like/heart burst, reaction picker, zap lightning flash |
| [04-publish-flow.md](04-publish-flow.md) | Filter carousel physics, filter application, share button, upload progress |
| [04b-sound-design.md](04b-sound-design.md) | The two opt-in sounds, mute-switch behavior, when sound plays |
| [05-navigation-and-chrome.md](05-navigation-and-chrome.md) | Tab bar, navigation push/pop, bottom sheets, context menus |
| [06-loading-and-feedback.md](06-loading-and-feedback.md) | Pull-to-refresh, skeleton shimmer, error shake, WoT trust badge |
| [07-video.md](07-video.md) | Video play/pause, control fade, seek scrubber |
| [08-polish-moments.md](08-polish-moments.md) | Onboarding feel, empty states (→10), error states, delight moments |
| [08b-surfaces-and-chrome.md](08b-surfaces-and-chrome.md) | Camera/picker, context menus, skeletons, feed-type transitions, "new posts" pill, typography |
| [09-text-inputs-settings.md](09-text-inputs-settings.md) | Text taps, input fields, toggles, sliders, settings microinteractions |
| [10-empty-states.md](10-empty-states.md) | Empty-state ambient animations (feed, profile, search, notifications) |
| [11-transition-completion.md](11-transition-completion.md) | Completion choreography for publish, zap, refresh, and other committed flows |

## Non-negotiables

1. **Behavioral parity.** Any change to a timing, curve, or haptic on one
   platform must land the equivalent change on the other in the same PR.
2. **Logic lives in Rust; native renders.** Native code owns *how* it looks and
   *when* the OS animates; it never decides *whether* an animation should run as
   a product rule. Animation triggers are driven by Rust-produced state
   snapshots (e.g. "post entered viewport," "zap confirmed"). Native owns the
   interpolation; Rust owns the state transition that starts it. Frame-by-frame
   interpolation never round-trips through the FFI boundary.
3. **60/120fps or it does not ship.** Every animation here must hold the display
   refresh rate (120Hz on ProMotion / high-refresh Android panels, 60Hz
   otherwise). No animation may drop a frame on the reference devices
   (iPhone 12 / Pixel 6 baseline).
4. **Respect Reduce Motion.** Every spring/parallax/burst in this spec has a
   Reduce-Motion fallback defined in [00-foundations.md](00-foundations.md).
   Honor `UIAccessibility.isReduceMotionEnabled` and Android
   `Settings.Global.ANIMATOR_DURATION_SCALE` / transitions-disabled.
