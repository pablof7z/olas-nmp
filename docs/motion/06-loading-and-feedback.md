# 06 — Loading & Feedback (Pull-to-refresh, Skeletons)

Tokens from `00-foundations.md`.

---

## 1. Pull-to-refresh

A custom indicator (Olas does **not** use the default platform spinner — it uses
a small Olas mark that "charges up"). The trigger is the load-bearing moment.

### 1.1 Haptic
- **At the trigger point** — the instant the pull distance crosses the refresh
  threshold and the gesture **commits** to refreshing: fire `Impact(Medium)` —
  iOS `UIImpactFeedbackGenerator(.medium)`, Android `EFFECT_CLICK`.
- Fire **once**, exactly at the crossing, while the finger is still down (this is
  what makes it feel mechanical — the user feels the "catch" before they release,
  like a camera shutter half-press hitting the stop).
- If the user drags back below threshold without releasing, the indicator
  **disarms** — fire `Impact(Soft)` once on the *downward* crossing to signal
  "released the catch." Debounce to one haptic per crossing direction.
- **No haptic** when the refresh *completes* (passive arrival of data — see `00`
  §4.1). The content fading in is the feedback.

### 1.2 Indicator states (what it looks like)
Pull distance `d`, threshold `D`. Define `p = clamp(d / D, 0, 1)`.

- **0% (`p` = 0, idle):** indicator hidden/0 opacity, scale 0.6.
- **0 → 50% (`p` ∈ (0, 0.5]):** the Olas mark fades in (opacity = `p` × 2,
  clamped) and scales `0.6 → 0.85`. It does **not** spin yet. A circular
  progress track draws clockwise, its trim = `p` (so at 50% the ring is half
  drawn). Everything tracks the finger 1:1, no easing.
- **50 → 100% (`p` ∈ (0.5, 1)):** ring continues to fill (trim = `p`); the mark
  scales `0.85 → 1.0`; a subtle glow ramps in. Still no spin. The motion is
  "charging."
- **Trigger point (`p` reaches 1):** fire `Impact(Medium)`. The ring snaps to a
  full closed circle via `spring.snappy`, the mark does a single `1.0 → 1.12 →
  1.0` pulse (`spring.bounce`). This is the visual "click."
- **Loading (after release, refresh in flight):** the closed ring converts to an
  indeterminate rotating arc (one segment, ~90° sweep) spinning at a constant
  rate (period 0.9s, linear). The mark holds at 1.0. This loop continues until
  Rust reports the refresh resolved.
- **Resolve:** indeterminate arc completes its current rotation to top-dead-
  center, then the indicator collapses (scale 1.0 → 0.6, opacity → 0 over
  `dur.base` `ease.in`) as the feed content settles into place. New content
  appears per §2. No haptic.

### 1.3 Reduce Motion
No spin/pulse; the indicator crossfades between charging/loading/done states over
`dur.fast`. The trigger haptic still fires (it is the primary feedback when
motion is reduced).

---

## 2. Loading skeleton behavior

### 2.1 When skeletons appear
- Show skeletons **only** if content is not ready within **120ms** of the view
  appearing (the "anti-flash" delay). If real content arrives in < 120ms, swap
  straight to it — never flash a skeleton for one frame.
- Once shown, a skeleton must remain visible for a **minimum of 280ms** before
  being replaced, even if data arrives sooner, to avoid a jarring flicker. (Rust
  can gate the snapshot to honor both thresholds; native just renders the state.)

### 2.2 Shimmer
- A single diagonal highlight band sweeps across all skeleton placeholders
  **left → right at a 20° angle**, `ease.shimmer` (linear), period **1.2s**, with
  a 0.2s gap between sweeps (so it pulses, not a continuous strobe).
- Band width ≈ 30% of the container; peak highlight opacity ≈ 0.12 over the base
  placeholder gray. Keep it subtle — a premium shimmer is barely there.
- All placeholders on screen share **one** synchronized sweep clock (the band
  appears to pass across the whole screen as one wave), not per-element clocks.

### 2.3 Placeholder geometry / aspect ratios
- **Feed photo card:** placeholder uses the post's **known** aspect ratio if Rust
  has it (from the event's `imeta`/dimension tags); otherwise default **4:5**
  (Olas's portrait-leaning default). Never reflow when the real image loads —
  the placeholder reserves the exact final box.
- **Grid thumbnail (profile/explore):** **1:1**.
- **Avatar:** circle, the row's avatar diameter.
- **Text lines:** rounded rects, height = line height, widths staggered
  (100% / 92% / 60%) so it reads as text, not bars. Username line 40% width.
- **Video card:** **16:9** unless known; show a static play glyph at 30% opacity
  (do not animate it).

### 2.4 Real content fade-in (instant swap vs crossfade)
- **Images / media:** **crossfade.** When the decoded image is ready, crossfade
  placeholder → image over `dur.base` (220ms) `ease.standard`. The placeholder's
  shimmer stops the moment the crossfade begins. No scale, no slide — just
  opacity. The box does not move (geometry was reserved in §2.3).
- **Text / metadata (username, caption):** **instant swap**, no crossfade. Text
  popping in crisply reads as "loaded"; crossfading text looks sluggish and
  blurs during the blend.
- **Whole-card first appearance** (card entering an already-loaded feed): card
  fades + rises 8pt over `dur.base` `ease.out`, with a 40ms stagger between
  consecutive new cards. This is distinct from the skeleton→content swap above.
- Reduce Motion: media crossfade becomes an instant swap (no opacity tween);
  card-entrance rise is dropped (fade only, or instant).

---

## 3. Error shake (shared primitive)

Referenced by `04` (upload) and `03` (zap) and any inline validation failure.
Canonical definition: 2 cycles, ±6pt horizontal, 90ms per cycle, `spring.snappy`,
paired with the relevant `Notify(*)` haptic fired once at shake start. Suppressed
under Reduce Motion (crossfade to the error state instead). Never loops; never
exceeds 2 cycles.
