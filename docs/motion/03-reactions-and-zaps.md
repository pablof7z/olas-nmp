# 03 — Reactions & Zaps (Like, Follow, Zap)

Covers the like/react microinteraction, the follow-button state machine, and the
zap-sent confirmation. Tokens from `00-foundations.md`. Every entry gives:
finger → change → order → timing, plus the haptic on both platforms.

---

## 1. Like / React

Two entry points produce the **same** committed state and the **same** haptic:
the inline heart button and the double-tap-on-media gesture.

### 1.1 Haptic
- **On like commit:** `Impact(Light)` — iOS `UIImpactFeedbackGenerator(.light)`,
  Android `EFFECT_TICK`.
- **On unlike:** **no haptic.** Removing a like is not a celebration; silence
  signals "undone." (This asymmetry is deliberate and load-bearing.)
- Fire the haptic at the **commit instant** (finger lift for the button; second
  tap registration for double-tap), simultaneous with the first frame of the
  fill animation — not before, not after.

### 1.2 Button press microinteraction (inline heart)
Finger goes **down** on the heart:
1. **T+0** (`dur.instant`, 80ms, `ease.out`): heart scales to **0.88** and dims
   to 70% opacity — the "pressed-in" affordance. This tracks the finger; if the
   user drags off before lifting, reverse it and **cancel** the like.

Finger **lifts** (commit):
2. **T+0:** fire `Impact(Light)`. State flips to liked in Rust snapshot.
3. **T+0 → 260ms** (`spring.bounce`, response 0.40 / damping 0.62): heart scales
   `0.88 → 1.18 → 1.0` (one visible overshoot) while the fill sweeps.
4. **Fill:** the outline heart fills from the **center outward** as a radial
   mask, 0→100% over **180ms** `ease.out`, color crossfading outline-gray →
   Olas red. The fill front *leads* the scale peak slightly so the heart looks
   like it "ignites" then "settles."
5. **Optional spark (NOT on Reduce Motion):** 6 tiny particles emit from the
   heart center at the overshoot peak (T+~120ms), travel 14–20pt outward, fade
   over 240ms. Particles are decorative; they never delay state.

### 1.3 Double-tap on media
Finger taps twice on the photo/video:
1. On the **second** tap-down: fire `Impact(Light)` and flip to liked
   (idempotent — double-tapping an already-liked post does nothing and stays
   silent).
2. A **large** heart (≈96pt) appears centered on the tap point at scale 0.6,
   springs to 1.0 via `spring.bounce`, holds 240ms, then fades + scales to 1.1
   over `dur.base` (220ms) `ease.in` and is removed.
3. The inline heart button updates to its filled state simultaneously (no
   separate haptic — one commit, one haptic).

### 1.4 Unlike behavior
Tap a filled heart:
1. **No haptic.**
2. Heart scales `1.0 → 0.9 → 1.0` via `spring.snappy` (subtle, no overshoot to
   speak of), fill drains center-inward over `dur.fast` (140ms), color
   crossfades red → outline-gray. No particles.

---

## 2. Follow / Unfollow

A four-state visual machine with one haptic, designed so the *Following* state
feels earned and the *Unfollow* affordance is intentionally slightly hidden.

### 2.1 States
`Follow` (filled accent button) → tap → `Following` (outlined/quiet button) →
later tap → confirm → `Unfollow` (transient label) → `Follow`.

### 2.2 Haptic
- **Follow commit:** `Impact(Light)` (iOS `.light` / Android `EFFECT_TICK`).
- **Unfollow commit:** **no haptic** (same undo-is-silent rule as unlike).

### 2.3 Follow → Following transition
Finger lifts on the *Follow* button:
1. **T+0:** fire `Impact(Light)`; Rust flips to following.
2. **T+0 → 80ms:** button label text crossfades "Follow" → checkmark glyph
   (`ease.standard`).
3. **T+0 → 220ms** (`spring.snappy`): button morphs from filled-accent to
   outlined-quiet: background color animates accent → transparent, border
   fades in, the button **contracts width** to fit the new label "Following."
4. **T+120ms:** a checkmark draws (stroke trim 0→1) over 160ms `ease.out`, then
   the label settles to "Following" with the check inline. Net feel: a confident
   "click into place."

### 2.4 Following → Unfollow (the deliberate-friction path)
Tap on *Following*:
1. **No immediate state change, no haptic yet.** The button label crossfades
   "Following" → "Unfollow?" (destructive red text) over `dur.fast`, and the
   button gives a 2pt horizontal settle via `spring.snappy`. This is a
   confirmation affordance, not a commit.
2. **Second tap within 3s** (or tap a confirm sheet on Android per Material):
   commit unfollow, **no haptic**, button morphs back to filled *Follow* via
   `spring.snappy`, label crossfades to "Follow."
3. **No second tap within 3s:** revert silently to *Following*.

> Rationale: following is cheap and celebrated (haptic + morph); unfollowing is
> rare and quiet (friction + silence). The asymmetry is the point.

---

## 3. Zap sent confirmation

A zap is a **payment** — the heaviest-meaning positive action in the app. It
earns the success notification haptic and (if enabled) the one opt-in sound.

### 3.1 Sequence (from payment ack)
The zap commits when Rust receives the payment success event. Everything below
keys off that ack, **not** off the tap.

1. **Tap → ack (network in flight):** the zap (lightning) button shows an
   indeterminate state — the bolt glyph pulses opacity 100%↔60% at 0.8s period
   (`ease.standard`, autoreverse). No haptic during flight.
2. **On ack — T+0:** fire `Notify(Success)` (iOS `.success` / Android composed
   waveform A). If the publish/zap sound is enabled, play `Sound(ZapChime)`
   simultaneously (see `04b-sound-design.md`).
3. **T+0 → 460ms** (`spring.bounce`, the `dur.deliberate` celebratory beat):
   the bolt scales `1.0 → 1.3 → 1.0`, fills with amber, and a single **lightning
   flash** sweeps diagonally across the post card: a soft amber gradient highlight
   travels corner-to-corner over 300ms `ease.out` at ~30% max opacity, then
   fades. (Suppressed under Reduce Motion → amber crossfade only.)
4. **T+120ms:** the zap amount (e.g. "+21") rises 12pt and fades in over 200ms,
   holds 600ms, drifts up 8pt more and fades out over 300ms.
5. The running zap total on the post increments with a `spring.snappy` odometer
   roll on the changed digits.

### 3.2 Zap failure
On payment failure ack: fire `Notify(Error)` (iOS `.error` / Android waveform C),
**no sound**, the bolt does a 2-cycle horizontal shake (±6pt, 90ms each via
`spring.snappy`) and returns to its idle unzapped state. Error copy appears as a
quiet inline label, not a celebratory toast. See `11-transition-completion.md`
§Failure.
