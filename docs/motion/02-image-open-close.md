# 02 — Image open/close (shared-element transition)

Covers spec item 2. This is the single most important transition in the app —
it is the moment that sells "premium and native." Budget engineering time here
accordingly.

---

## 2.1 Open: grid thumbnail → full screen

The user taps a thumbnail in a grid (profile grid or group grid). The thumbnail
**expands in place** into the full-screen viewer; it does not cross-dissolve and
it does not slide a new screen over the old one.

### Geometry
- The tapped thumbnail is the **shared element**. Its frame (origin + size) is
  captured at tap time. The full-screen image animates from that exact frame to
  its final aspect-fit frame.
- **Scale origin:** the center of the tapped thumbnail's frame in screen
  coordinates — not the screen center. The expansion appears to grow *out of the
  thumbnail the finger touched*.
- The image maintains aspect during the move (the thumb is center-cropped, the
  full view is aspect-fit): interpolate both the frame and the crop inset so
  there is no visible "jump" in framing. Use a `contentsRect`/matched-crop
  interpolation, not a naive frame scale.

### Timing & curve
- **Curve: `spring.gentle`** (response 0.45, damping 1.00 — no overshoot; an
  image must not wobble).
- Effective settle ≈ **450ms**; the main move reads as ~350ms.
- The transition is **interruptible**: if the user starts a dismiss drag before
  it settles, hand the in-flight geometry to the dismiss gesture (velocity
  preserved). iOS: `UIViewPropertyAnimator` with `pausesOnCompletion` or
  SwiftUI `matchedGeometryEffect` + gesture; Compose: `SharedTransitionLayout`
  with the bounds animation driven by an interruptible `Animatable`.

### Background fade
- Behind the shared image, a black scrim fades **opacity `0 → 1`** over
  **`dur.base` (220ms)**, `ease.standard`. It reaches full black slightly before
  the image lands (250ms vs 350ms) so the destination feels solid on arrival.
- The surrounding grid/chrome simultaneously fades out (opacity `1 → 0`,
  `dur.fast` (140ms)) so it does not show through the scrim during the move.

### Chrome (caption, actions, close button)
- The viewer's overlay chrome fades in **after** the image lands: start at
  300ms, `dur.fast` (140ms), `ease.out`. The image is the star; controls
  arrive a beat later.

### Haptic
- `Impact(Light)` on tap-down acknowledgement only if the open is committed.
  No haptic at landing (the visual settle is enough; a haptic here would feel
  gratuitous on every photo open).

**Principle:** *physical continuity* — the photo the user touched is the photo
that fills the screen; nothing is created or destroyed, only resized.

## 2.2 Close: full screen → grid (button or swipe)

### Tap close / back
- Reverse of open: the image animates back to the **original thumbnail frame**
  (re-query it; the grid may have scrolled — if the source is now off-screen,
  fall back to a fade-down to the nearest grid edge).
- Same `spring.gentle`, scrim fades `1 → 0` over `dur.base`.

### Interactive pinch / drag dismiss (primary gesture)
- **Drag down (or pinch-in)** to dismiss. This is the expected gesture and must
  feel rubber-banded to the finger.
- While dragging:
  - Image **follows the finger 1:1** in translation.
  - Image **scales down** proportional to drag distance:
    `scale = 1 − (dragDistance / screenHeight) * 0.4`, clamped to `[0.6, 1.0]`.
  - Corner radius interpolates `0 → thumbnailCornerRadius` on the same fraction
    so it visually "becomes a thumbnail again."
  - Scrim opacity = `1 − dragFraction` (background reveals as you pull away).
  - This is **scroll-linked**, not animated — locked to the finger.
- **Release thresholds:**
  - Dismiss if `dragDistance > 25% of screen height` **OR**
    `verticalVelocity > 1000 pt/s`.
  - On dismiss: continue to the thumbnail frame with `spring.gentle`, carrying
    the release velocity into the spring's initial velocity.
  - On cancel (below threshold): snap back to full-screen with `spring.snappy`
    (response 0.30) — a quick, decisive return.
- **Haptic:** `Impact(Soft)` at the instant the dismiss threshold is crossed
  *during* the drag (so the user feels "let go now and it'll close"), fired once
  per crossing (re-armed if they drag back below).

**Principle:** *the gesture owns the pixels.* Until release, every value tracks
the finger; the spring only takes over to finish what the finger started.

## 2.3 Paging between images in the viewer
- Horizontal paging is native paged scroll (iOS `isPagingEnabled` /
  `UIPageViewController`; Compose `HorizontalPager`).
- Snap: platform paging fling. Add `Selection` on each page commit
  (when a new photo becomes the settled page).
- Adjacent images preload one ahead/behind so paging never reveals a
  placeholder during a fast swipe.

**Reduce Motion:** open/close becomes a `dur.base` crossfade with no scale and
no geometry match (see 00 §6); drag-dismiss still tracks the finger for
translation but applies no scale or corner-radius morph.
