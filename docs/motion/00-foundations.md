# 00 — Foundations: tokens, curves, haptics, platform mapping

This file is the authoritative shared vocabulary. Every other spec file
references these tokens by name. Implement them once as platform constants;
never inline a raw spring, bezier, duration, or haptic where a token exists.
If a later file uses a token, its definition is here.

---

## 1. Spring tokens

Springs are specified by the physically meaningful pair **(response, damping
fraction)** per Apple's `Animation.spring(response:dampingFraction:)`. `response`
is the approximate time (seconds) for the spring's main move; `dampingFraction`
is 1.0 = critically damped (no overshoot), <1.0 = overshoots (bouncy).

For Compose, convert with the exact relations (not approximations):

```
stiffness    = (2π / response)^2
dampingRatio = dampingFraction
```

| Token | response | damping | Compose stiffness | Compose dampingRatio | Use |
|-------|----------|---------|-------------------|----------------------|-----|
| `spring.snappy`   | 0.30 | 0.85 | 438 | 0.85 | Tab pop, toggles, pill travel, small UI state, error shake |
| `spring.gentle`   | 0.50 | 0.90 | 158 | 0.90 | Sheets, nav, shared element, content shifts — **no overshoot** |
| `spring.bounce`   | 0.40 | 0.62 | 247 | 0.62 | Like fill, icon pop, menu expand, reward moments — **one visible overshoot** |
| `spring.carousel` | 0.35 | 0.80 | 322 | 0.80 | Filter-carousel detent settle — crisp, near-zero overshoot |
| `spring.stiff`    | 0.22 | 0.90 | 815 | 0.90 | Scrubber thumb, drag-follow elements |

A spring has no fixed duration; `response` is design intent. Where a hard
duration is required (crossfades, progress, fades) use the curve + duration
tokens below.

Compose notes: use `androidx.compose.animation.core.spring()`. For `Dp`/`Offset`
set `visibilityThreshold = Dp.VisibilityThreshold` / `Offset.VisibilityThreshold`.

## 2. Curve (easing) tokens

For fades, color/opacity, and any fixed-duration tween. cubic-bezier control
points `(x1, y1, x2, y2)`.

| Token | cubic-bezier | iOS | Compose | Use |
|-------|--------------|-----|---------|-----|
| `ease.standard` | (0.4, 0.0, 0.2, 1.0) | `timingCurve` / `.easeInOut` | `FastOutSlowInEasing` | Default tween, crossfades |
| `ease.out`      | (0.0, 0.0, 0.2, 1.0) | `.easeOut` | `LinearOutSlowInEasing` | Elements entering / settling |
| `ease.in`       | (0.4, 0.0, 1.0, 1.0) | `.easeIn` | `FastOutLinearInEasing` | Elements leaving screen |
| `linear`        | (0.0, 0.0, 1.0, 1.0) | `.linear` | `LinearEasing` | Shimmer sweep, spinners |

## 3. Duration scale

Use these named durations; do not invent intermediate values.

| Token | ms | Use |
|-------|----|-----|
| `dur.instant`    | 80   | Press-in tint, selection flash |
| `dur.fast`       | 140  | Icon crossfade, small fades, fill drain |
| `dur.base`       | 220  | Standard transitions, filter/preview crossfade |
| `dur.slow`       | 360  | Navigation push/pop, sheet present/dismiss |
| `dur.deliberate` | 460  | Celebratory beats (like settle, zap, publish) |
| `dur.ambient`    | 1200 | Looping shimmer cycle, idle pulses |

## 4. Haptic & sound tokens

Single source of truth. Never fire a haptic not listed here.

### 4.1 Tiers

| Token | iOS (`UIFeedbackGenerator`) | Android | Used for |
|-------|------------------------------|---------|----------|
| `Selection`      | `UISelectionFeedbackGenerator.selectionChanged()` | `EFFECT_TICK` / `CLOCK_TICK` | Carousel detent, tab change, WoT preset, scrubber notch |
| `Impact(Light)`  | `UIImpactFeedbackGenerator(.light)` | `EFFECT_TICK` | Like commit, follow commit, onboarding advance |
| `Impact(Soft)`   | `UIImpactFeedbackGenerator(.soft)` | oneShot ~18ms @ 90/255 | Sheet detent snap, photo-dismiss threshold |
| `Impact(Rigid)`  | `UIImpactFeedbackGenerator(.rigid)` | `EFFECT_HEAVY_CLICK` / oneShot ~12ms @160/255 | Custom long-press menu pop, pull-to-refresh trigger |
| `Notify(Success)`| `.notificationOccurred(.success)` | waveform A `[0,30,60,30]` | Publish accepted, zap confirmed, onboarding done |
| `Notify(Warning)`| `.notificationOccurred(.warning)` | waveform B `[0,40,40,40]` | Blocked action, recoverable warning |
| `Notify(Error)`  | `.notificationOccurred(.error)` | waveform C `[0,60,40,60]` | Publish/zap failure (pairs with error shake) |

### 4.2 Severity doctrine (load-bearing)
- **`Selection`** = a discrete value changed under the finger. No consequence.
- **`Impact(*)`** = a deliberate commit with light/medium/heavy weight.
- **`Notify(*)`** = a *consequence* landed (a thing succeeded/failed in the world).
- **Undo is silent.** Unlike, unfollow, and removing a value fire **no haptic**.
  Adding/committing haptics; removing does not. This asymmetry is intentional.

### 4.3 Sound
| Token | Asset | Used for |
|-------|-------|----------|
| `Sound(ShutterSoft)` | soft shutter, ~120ms | Publish accepted, onboarding done (opt-in) |
| `Sound(ZapChime)`    | bright chime, ~200ms | Zap confirmed (opt-in) |

Sound is **opt-in, default off**, gated on a Rust-owned `soundEnabled` snapshot
field. These are the *only* two sounds in the app. Play simultaneously with the
paired `Notify(Success)`.

### 4.4 Firing rules
- **Prepare before fire.** iOS: call `.prepare()` ~150ms before the expected
  event (e.g. on drag begin / touch-down for carousel and scrubber) to remove
  Taptic latency. Re-prepare if more are imminent.
- Never fire two haptics within 50ms; coalesce to the higher-severity one.
- All haptics/sounds are gated on Rust-owned `hapticsEnabled` / `soundEnabled`
  snapshot fields (user setting AND system setting). Native reads the flag; it
  never decides policy.

## 5. Platform mapping rules

- A spec entry's spring/curve/duration/haptic tokens are authoritative. Where a
  platform lacks a primitive, emulate the *feel*, not the API.
- iOS shared-element and modal work: SwiftUI `matchedGeometryEffect` /
  `NavigationTransition` (iOS 18), UIKit `UIViewControllerAnimatedTransitioning`
  fallback for finer control.
- Compose: `SharedTransitionLayout` / `AnimatedContent` / `Modifier.animateItem`.
  Predictive-back must drive the same shared-element transition (see 05).
- Frame source of truth for custom particle/scrub work: iOS `CADisplayLink` /
  `TimelineView`; Compose `withFrameNanos`. **Never** `Timer`-drive animation —
  also forbidden by the repo's no-polling rule.
- Animation *triggers* come from Rust-produced state snapshots (e.g. "post
  entered viewport," "relay accepted," "zap confirmed"). Native owns the
  interpolation; Rust owns the state transition. Per-frame values never cross FFI.

## 6. Reduce-Motion fallbacks (global)

| Normal | Reduced |
|--------|---------|
| Spring move / parallax | `ease.standard` opacity crossfade at `dur.fast`; no positional travel, no overshoot. |
| Shared-element zoom | Plain crossfade `dur.base`, no scale. |
| Like burst / particles / zap flash / confetti | Final glyph at full opacity instantly + the specified haptic; particles and flash suppressed. |
| Pull-to-refresh custom animation | Static logo + standard system spinner; no morph. |
| Skeleton shimmer | Static 8%-opacity placeholder blocks; no sweep. |
| Error shake | No positional shake; red border/`!` crossfade `dur.fast` + `Notify(Error)`. |
| Tab/icon pop | Opacity + tint change only, no scale overshoot. |

Reduce Motion never removes a **haptic** — only visual motion. Reduce Motion is
read from `UIAccessibility.isReduceMotionEnabled` (iOS) and Android
`ANIMATOR_DURATION_SCALE == 0` / transitions-disabled, surfaced to Rust which
sets a `reduceMotion` snapshot field native reads.
