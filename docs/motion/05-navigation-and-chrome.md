# 05 — Navigation & Chrome (Tab bar, Sheets, Context menu, Onboarding)

Tokens from `00-foundations.md`. Each entry: finger → change → order → timing +
haptic on both platforms.

---

## 1. Tab bar selection

### 1.1 Haptic
- **On switching to a different tab:** fire `Selection` — iOS
  `UISelectionFeedbackGenerator.selectionChanged()`, Android `EFFECT_TICK`.
- **No haptic** when re-tapping the already-active tab (that gesture instead
  scrolls-to-top / pops to root — see §1.3).
- Severity rationale: a tab change is a discrete selection, not a commit with a
  consequence. Anything heavier than `Selection` here feels cheap.

### 1.2 Visual
Finger taps an inactive tab:
1. **T+0:** fire `Selection`. The icon crossfades outline → filled variant over
   `dur.fast` and the label/icon color animates to accent.
2. The tapped icon does a small `spring.snappy` scale `1.0 → 1.12 → 1.0`
   (the only motion; siblings do nothing).
3. The content area swaps instantly (no slide between tabs — tabs are peers, not
   a stack). If the destination needs to load, show skeletons per `06`.

### 1.3 Re-tap active tab
No haptic on the tap itself. If the tab scrolls to top, the scroll uses the
platform's standard scroll-to-top animation. If it reaches an *already*-at-top
state and there is nothing to do, do nothing (no haptic, no bounce).

---

## 2. Sheet drag-to-dismiss snap

Bottom sheets (comments, share targets, profile actions, settings pickers) are
draggable. They have at most two detents (e.g. medium, large) plus dismissed.

### 2.1 Haptic
- **On snapping to a detent** (including snapping *back* up after an incomplete
  dismiss drag): fire `Impact(Soft)` — iOS `UIImpactFeedbackGenerator(.soft)`,
  Android oneShot 18ms @ ~90/255 (or `EFFECT_TICK` on devices without amplitude
  control). Soft = cushioned, matching the rubber-banding feel.
- **On crossing the dismiss threshold** (the sheet commits to closing): fire
  `Impact(Soft)` once at the threshold crossing, then let it animate out. Do
  **not** fire a second haptic when it finishes closing.
- **No haptic** while dragging between detents — only at the snap.

### 2.2 Physics
- The sheet tracks the finger 1:1 within range; past the top detent it
  rubber-bands (resistance curve, ~0.55 multiplier).
- Release → nearest detent via `spring.gentle` (response 0.50 / damping 0.90,
  no overshoot — sheets must not bounce).
- Dismiss threshold: dragged past 35% of sheet height **or** flicked with
  downward velocity > 800pt/s. On commit, sheet animates down `dur.slow`
  `ease.in`; the dimming scrim fades 1→0 over the same duration.

---

## 3. Long-press context menu appearance

### 3.1 Haptic
- **At the moment the menu commits to appearing** (long-press recognized): fire
  `Impact(Rigid)` — iOS `UIImpactFeedbackGenerator(.rigid)`, Android
  `EFFECT_HEAVY_CLICK` (or oneShot 12ms @ ~160/255). Rigid = a crisp mechanical
  "pop," the tactile signature of a peek/menu.
- iOS note: the system `UIContextMenuInteraction` already plays its own haptic.
  When using the native context menu API, **do not add a second haptic** — let
  the system own it. The `Impact(Rigid)` spec above applies only to *custom*
  long-press menus where the system does not provide one. (Rust emits the effect
  only for the custom path; the native path is system-owned.)

### 3.2 Visual
Finger holds:
1. During the press, the target view scales down slightly to **0.96** over the
   recognizer's minimum duration (~`dur.base`), signaling "something is coming."
2. **On recognition — T+0:** fire `Impact(Rigid)`. The target lifts (shadow
   grows, scale springs to 1.04 via `spring.snappy`), the rest of the screen
   blurs/dims, and the menu expands from the target's edge: scale `0.8 → 1.0`
   from the anchor corner via `spring.bounce`, opacity 0→1 over `dur.fast`.
3. Releasing onto an item: that row flashes a selection highlight
   (`dur.instant`), menu collapses back into the anchor (`spring.snappy`),
   target returns to 1.0. The selected *action* may itself produce a haptic per
   its own rule (e.g. choosing "Like" → `Impact(Light)`).

---

## 4. Onboarding step advance ("Continue")

### 4.1 Haptic
- **On each Continue tap that advances a step:** fire `Impact(Light)` — iOS
  `.light`, Android `EFFECT_TICK`. Light and forward-leaning: progress, not
  consequence.
- **On the final step's "Done"/"Start"** (entering the app): this *is* a
  consequence → fire `Notify(Success)` (iOS `.success` / Android waveform A) and,
  if enabled, the opt-in `Sound(ShutterSoft)`. Onboarding completes like a
  publish completes — it is a real accomplishment.

### 4.2 Visual
1. **T+0:** fire `Impact(Light)`. The Continue button does a quick press-in
   (scale 0.96, `dur.instant`) on touch-down, releasing to 1.0 on commit.
2. The outgoing step slides left and fades (`dur.slow` `ease.in`, ~24pt travel);
   the incoming step slides in from the right (`spring.gentle`). Parallax: the
   illustration layer moves ~1.4× the text layer for depth.
3. The progress indicator (dots or bar) advances via `spring.snappy`.
4. Reduce Motion: replace slide+parallax with a `dur.base` opacity crossfade;
   haptic unchanged.

---

## 5. Navigation push / pop (stack transitions)

Pushing onto and popping off a navigation stack (feed → profile, profile →
post, settings drill-down). Distinct from tab switches (§1, peers, instant) and
modal sheets (§2). A push is a *hierarchical move deeper*; the platform idiom
must be honored exactly.

### 5.1 Push (forward)
- **iOS:** the system horizontal slide. Incoming view slides in from the
  trailing edge (full width travel), outgoing view parallaxes out at **~0.3×**
  (moves left ~30% of width) and dims under a thin scrim — the native
  `UINavigationController` push. Duration **`dur.slow` (360ms)**, `ease.standard`
  (the system curve). Use `navigationTransition` / push; **do not** hand-roll the
  slide — match the OS so back-swipe interop is free.
- **Android:** Material **shared-axis X** transition — incoming enters from
  +30dp trailing with opacity `0 → 1`, outgoing exits to −30dp leading with
  opacity `1 → 0`. Duration **`dur.slow` (360ms)**, `ease.standard`
  (`FastOutSlowInEasing`). Compose `AnimatedContent` with
  `slideInHorizontally + fadeIn` / `slideOutHorizontally + fadeOut`, or the
  Material motion `materialSharedAxisX`.
- **Haptic:** none. Navigation is not a commit; the slide is its own feedback.

### 5.2 Pop (back — button / programmatic)
- Exact reverse of the push (`dur.slow`, `ease.standard`). iOS: trailing-edge
  slide-out, the underlying view un-parallaxes from −30% back to 0. Android:
  shared-axis X reversed.

### 5.3 Interactive back (the important one)
- **iOS edge-swipe back:** native `interactivePopGestureRecognizer`. The pop
  tracks the finger **1:1** from the screen's leading edge; the underlying
  (previous) view un-parallaxes in lockstep. This is **scroll-linked**, not
  timed. Release thresholds: complete the pop if dragged past **50% width** OR
  flicked with horizontal velocity **> 800 pt/s**; otherwise cancel back to the
  pushed view. Completion/cancel finishes on `spring.gentle` carrying the release
  velocity. Keep the system recognizer; never replace it.
- **Android predictive back:** wire the **predictive-back** API
  (`OnBackPressedCallback` + `BackHandler` with progress). As the user drags from
  the edge, the current screen scales down slightly (to ~0.92) and slides toward
  the gesture, revealing the previous screen beneath — the Android 14+ predictive
  idiom, progress-linked to the drag. Commit/cancel on the same shared-axis X
  finish via `spring.gentle`.
- **Haptic:** none on either platform for the gesture itself.

### 5.4 Cross-fade exception (root replacement)
- When swapping the *root* of a flow (e.g. onboarding → app, login → feed) there
  is no hierarchy to slide. Use a **cross-fade** over `dur.slow` `ease.standard`
  with no horizontal travel — a root change is a context change, not a push.

**Principle:** *depth is directional.* Forward slides in from the trailing edge,
back tracks the finger home; honoring each OS's native push/back means the app
inherits years of muscle memory for free, and interactive back never feels
bolted on.

### 5.5 Reduce Motion
Push/pop become an opacity cross-fade over `dur.base`, no horizontal travel, no
parallax. Interactive back still tracks the finger for *dismissal intent* but
applies opacity only (no slide/scale of the underlying view).
