# 01 — Feed scroll, overscroll, header parallax, post card appearance

Covers spec items 1 and 8.

---

## 1.1 Feed scroll physics

The feed is the home of the app; its scroll must feel like the platform's own —
because users compare it directly to Instagram, which itself uses each
platform's native scroller.

**Decision: do not reimplement scroll physics. Use the native scroller and tune
only the exposed knobs.** A hand-rolled physics engine is forbidden here — it
will never match the OS's pixel-for-pixel inertia and would fight the no-polling
rule.

### iOS
- Use `UICollectionView` (compositional layout) or SwiftUI `ScrollView` +
  `LazyVStack`. Keep `UIScrollView` defaults:
  - `decelerationRate = .normal` (0.998 per ms; this is the platform feed feel).
    Do **not** use `.fast`.
  - `alwaysBounceVertical = true`.
- Deceleration is the standard iOS exponential model; do not override
  `setContentOffset` mid-fling.

### Android
- Use Compose `LazyColumn` with the default `ScrollableState`.
- Fling uses `androidx.compose.animation.splineBasedDecay` (the platform spline)
  via the default `flingBehavior = ScrollableDefaults.flingBehavior()`. Keep it.
- The decay friction multiplier stays at the platform default
  (`SplineBasedFloatDecayAnimationSpec`, ~`0.015` `friction`). Do not retune.

**Haptic:** none. Scrolling, fling, and rubber-band carry **no** haptic — the
platform scroller is silent and users expect that; adding feedback to raw
scrolling feels cheap and fights the native feel. (Haptics enter only at
discrete events layered on top: pull-to-refresh trigger in 06, page commits in
02.)

**Principle:** *feels native by being native.* The differentiator is what we
layer on top (parallax, appearance), not the inertia curve.

## 1.2 Overscroll behavior

| Platform | Behavior |
|----------|----------|
| iOS | Native rubber-band bounce at top and bottom. The pull-to-refresh affordance (see 06) lives in the top overscroll region; bottom overscroll is pure rubber-band. |
| Android | Material 3 **stretch overscroll** (`OverscrollEffect`, the default content stretch/glow). Keep the default. Top overscroll hosts pull-to-refresh. |

Rubber-band/stretch resistance: platform default. No custom resistance curve.
**Principle:** *honor muscle memory* — users already know exactly how far a pull
travels on their OS.

## 1.3 Header parallax

The feed top hosts a group/identity header (avatar row + title) that parallaxes
as the feed scrolls under it.

- **Travel ratio:** header content moves at **0.5×** the scroll offset (moves up
  half as fast as the feed) until it is half-collapsed, producing depth.
- **Collapse range:** header height animates from `expandedHeight` (e.g. 132pt)
  to `collapsedHeight` (the nav bar height, e.g. 44pt) over the first
  `expandedHeight − collapsedHeight` points of scroll, clamped.
- **Title:** crossfades from large (28pt) to inline (17pt) using `ease.standard`
  driven by the **scroll fraction**, not a timed animation — it is a
  scroll-linked interpolation (0→1 maps directly to collapse progress). No
  spring; scrubbing must be reversible and frame-perfect.
- **Avatar:** scales `1.0 → 0.7` and translates into the collapsed bar on the
  same scroll fraction.
- **Background blur:** the header's translucent material (`.ultraThinMaterial` /
  Compose `Modifier.blur` + scrim) ramps opacity `0 → 1` over the first 24pt of
  scroll so content gains a legible backdrop the instant it scrolls under.
- **Settle on release:** if a fling ends with the header partially collapsed,
  snap to the nearest of {expanded, collapsed} using `spring.gentle`. No
  partial resting state.

Implementation: drive all of the above from a single `collapseFraction`
(0…1) derived from `contentOffset.y`. iOS: `UIScrollViewDelegate`
`scrollViewDidScroll` or SwiftUI `GeometryReader`/`onScrollGeometryChange`
(iOS 18). Compose: `LazyListState.firstVisibleItemScrollOffset` +
`nestedScroll`. **Never** sample on a timer.

**Principle:** *depth without distraction* — parallax communicates hierarchy;
scroll-linked (not time-linked) keeps it locked to the finger.

## 1.4 Post card appearance (entering the feed)

How a post enters as it scrolls into view, or when newly fetched posts prepend.

### Scroll-in (post enters viewport during normal scroll)
- **No entrance animation.** Cells are fully rendered before they cross the
  viewport edge. Animating cells on scroll-in is the classic "web wrapper" tell
  and fights scroll performance. Cells appear instant and complete.
- Exception: image/video *content within* a card fades from its blurhash
  placeholder (see 06 / 07) — the card frame itself never animates on scroll.

### New posts prepended (fresh fetch after pull-to-refresh, or live arrival)
- New cells **fade-up**: opacity `0 → 1` with a `+12pt` vertical translate
  settling to 0.
- Curve: `ease.out`, **`dur.base` (220ms)**.
- **Stagger:** 40ms delay between consecutive new cells, capped at **5 cells**
  (cells beyond the 5th appear with the 5th's timing — no long cascade).
- Only items that are newly inserted animate; existing items below shift using
  the list's native item-move animation (`spring.gentle`), not a fade.
- iOS: `UICollectionView` `performBatchUpdates` with a custom layout-attributes
  animator, or SwiftUI `.transition(.move+.opacity)` keyed on stable IDs.
  Compose: `LazyColumn` `item(key=)` + `Modifier.animateItem()` with the
  appearance handled by an `AnimatedVisibility` keyed on first-seen.
- **No stagger when count > 20** (e.g. first full page load): show the page
  instantly to avoid a slow waterfall. Stagger is for small live deltas only.

**Principle:** *calls attention to what's new without making the feed feel
slow.* Motion is reserved for genuine deltas; bulk content is instant.
