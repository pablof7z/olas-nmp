# 04 — Publish Flow (Filter Carousel, Publish, Upload, WoT preset)

Tokens from `00-foundations.md`. Each entry: finger → change → order → timing +
haptic on both platforms.

---

## 1. Filter carousel snap

A horizontally paged carousel of filter thumbnails with **detents** — each
filter snaps to center. This is the canonical "selection detent" interaction.

### 1.1 Haptic
- **Each time a new filter snaps to the centered/selected position:** fire
  `Selection` — iOS `UISelectionFeedbackGenerator.selectionChanged()`, Android
  `EFFECT_TICK` (or `CLOCK_TICK`).
- **Critical detail:** fire on the **detent crossing**, i.e. the instant the
  centered item *changes from one filter to the next* during the drag, not on
  finger lift. Dragging quickly across 5 filters fires **5** selection ticks,
  one per item passed — like a physical click-wheel. Prime the generator on
  `touchDown` so the first tick has zero latency.
- **Do not** fire if the drag returns to the same filter it started on.
- **No haptic** on the final settle if no detent changed.

### 1.2 Physics
- Drag tracks 1:1 with the finger. On release, momentum carries to the nearest
  detent via `spring.carousel` (response 0.35 / damping 0.80) — crisp settle,
  effectively no overshoot.
- The **centered** thumbnail is scaled 1.0; neighbors 0.86 and dimmed to 60%.
  Scale/opacity interpolate continuously with scroll position so the focus
  "breathes" as you drag. The live preview above the strip crossfades to the
  newly-centered filter over `dur.fast` as each detent commits.

---

## 2. Post published — success

The publish commit keys off the **relay-accepted** ack from Rust, never off the
"Share" tap. Full choreography in `11-transition-completion.md`; haptic/sound
summary here.

### 2.1 During upload
- Share tapped → button morphs to a determinate progress ring (upload %). The
  ring fills with the upload progress reported by Rust. **No haptic during
  upload.** No sound.
- If upload is multi-stage (blob upload → event publish), the ring shows blob %
  then a brief indeterminate spin while the Nostr event propagates.

### 2.2 On relay-accepted ack
1. **T+0:** fire `Notify(Success)` (iOS `.success` / Android waveform A). If the
   publish sound is enabled, play `Sound(ShutterSoft)` simultaneously (see
   `04b-sound-design.md` — this is the opt-in publish sound).
2. The progress ring's final segment snaps closed and morphs into a checkmark
   (stroke trim 0→1, 200ms `ease.out`) inside the now-green button via
   `spring.bounce`.
3. **T+460ms:** the composer dismisses (`dur.slow` `spring.gentle` downward
   sheet) and the new post is already present at the top of the feed (inserted
   by Rust), gently highlighted once (see `07`).

---

## 3. Error / failed upload

### 3.1 Haptic
- **On failure ack:** fire `Notify(Error)` (iOS `.error` / Android waveform C).
  **No sound.**

### 3.2 Visual
1. The progress ring turns destructive-red and morphs to a small "!" glyph.
2. The button/row does a 2-cycle horizontal shake: ±6pt per cycle, 90ms each,
   via `spring.snappy` (the canonical "error shake," shared across the app).
3. A quiet inline retry affordance appears beneath ("Couldn't upload · Retry").
   This is **not** a toast and does not auto-dismiss. Tapping Retry returns to
   §2.1 with **no** haptic (retry is a fresh attempt, its result will haptic).
4. Reduce Motion: skip the shake; crossfade the button to the red "!" state over
   `dur.fast`. Haptic still fires.

---

## 4. WoT filter preset change

The Web-of-Trust filter is a small segmented control / preset chips (e.g.
"Friends," "Friends of friends," "Everyone"). Changing it re-scopes the feed.

### 4.1 Haptic
- **On committing a new preset:** fire `Selection` — iOS
  `UISelectionFeedbackGenerator.selectionChanged()`, Android `EFFECT_TICK`. It is
  a discrete value change under the finger, identical tier to a carousel detent.
- **No** notification haptic — changing a filter is not a consequence, it is a
  selection. **No** haptic if the user taps the already-active preset.

### 4.2 Visual
1. **T+0:** fire `Selection`. The selection pill slides from the old chip to the
   new chip via `spring.snappy` (the pill is a shared element; it does not fade,
   it *travels*).
2. The feed below performs a content swap, not a jarring reload: existing cards
   fade to 40% over `dur.fast`, the newly-scoped set fades in over `dur.base`
   with a 40ms stagger per card (`ease.standard`). See `06-loading-and-feedback.md`
   for the skeleton rules if the new scope must fetch.
3. A small trust-badge count near the control updates with a `spring.snappy`
   odometer roll (e.g. "1.2k" → "340").

> Note on severity: presets and carousel both use `Selection`, never an impact.
> If a WoT preset *blocks* an action the user attempted (e.g. trying to reply to
> an out-of-web account), that is a different event → `Notify(Warning)`
> (Android waveform B). See `00` §4.2.
