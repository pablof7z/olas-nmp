# 07 — Video (Play/pause, control fade, seek scrubber)

Covers spec item 15. Tokens from `00-foundations.md`. Each entry: finger →
change → order → timing + haptic on both platforms.

Olas videos appear in two contexts: **inline in the feed** (autoplay, muted, no
chrome) and **full-screen in the viewer** (the focused, controllable surface).
The motion rules differ by context and are specified separately.

Platform players: iOS `AVPlayer` / `AVPlayerLayer` (or `VideoPlayer` in
SwiftUI); Android `ExoPlayer` (Media3) inside a Compose `AndroidView` /
`PlayerView`. Native owns playback transport; **Rust owns which item is the
"active" autoplay target** (derived from viewport state) and pushes it as a
snapshot field. Native never decides *which* video plays — only renders the
transport for the one Rust marks active.

---

## 1. Inline feed video (autoplay, muted)

### 1.1 Autoplay enter/exit
- A feed video begins playing **muted** when Rust marks it the active viewport
  item (the post whose media is most centered, single active item at a time).
- **Fade-in of frames:** the first decoded frame crossfades up from the poster
  (blurhash → poster → live frames) over **`dur.base` (220ms)** `ease.standard`.
  No scale, no slide — the still simply comes alive.
- **Exit (scrolls away):** playback pauses and the layer crossfades back to the
  poster frame over `dur.fast` (140ms). No haptic. Decoding is released by Rust's
  active-item change, not by native heuristics.

### 1.2 Mute affordance
- A small speaker glyph sits bottom-trailing. Tapping it unmutes:
  - Glyph crossfades muted ↔ unmuted over `dur.fast`, with a `spring.snappy`
    scale `1.0 → 1.15 → 1.0`.
  - **Haptic:** `Selection` (a discrete state toggle under the finger). Muting
    again: also `Selection` — this is a toggle, not an undo, so both directions
    tick (the undo-is-silent rule applies to destructive removals, not to a
    symmetric mute toggle).
- Only **one** video is unmuted at a time; if another unmutes, the prior reverts
  to muted with a silent `dur.fast` glyph crossfade (Rust owns the
  single-unmuted invariant).

### 1.3 Progress hairline
- Inline videos show a 2pt progress hairline pinned to the bottom edge, filling
  `linear` with playback time. It is **not** interactive inline (tap opens the
  full-screen viewer). It fades in with the first frame (`dur.base`) and is
  always present thereafter at 40% opacity.

**Principle:** *ambient, not demanding.* Inline video is alive but quiet — it
animates only to come into and out of existence; it offers no chrome to fiddle.

---

## 2. Full-screen viewer video

Opened via the shared-element transition in `02`. Here the video is the focused
surface and gains full transport controls.

### 2.1 Controls auto-hide / reveal
Controls = play/pause button, scrubber, time labels, mute, close.

- **On open:** controls fade **in** after the shared-element image lands — start
  at 300ms, `dur.fast` (140ms), `ease.out` (matches `02` chrome timing).
- **Auto-hide:** if the video is playing and the user does not interact for
  **3.0s**, all controls fade **out** over **`dur.base` (220ms)** `ease.in`,
  translating down 8pt as they go (controls "settle away"). The video keeps
  playing full-bleed.
- **Reveal:** a single tap anywhere fades controls back **in** over `dur.fast`
  (no translate; they snap to presence). The 3.0s idle timer restarts on every
  interaction. (The idle timer is an OS-side UI affordance, allowed under the
  no-polling rule as a debounced gesture timer, **not** as state polling.)
- **While paused:** controls **never** auto-hide — a paused video always shows
  its transport. Auto-hide applies only during playback.
- **Haptic:** none for show/hide. Revealing chrome is not a commit.

### 2.2 Play / pause
- **Tap the play/pause button** (or a single tap on the video when controls are
  already visible toggles play/pause):
  - The glyph **morphs** play ⟷ pause (two bars ⟷ triangle) via an
    interpolated path morph over **`dur.fast` (140ms)** `ease.standard` — not a
    crossfade of two glyphs; the shape transforms.
  - A brief **scale acknowledgement**: `1.0 → 0.88 → 1.0` on touch-down/up via
    `spring.snappy`.
  - **On pause:** a momentary center overlay of the pause glyph (scale
    `0.8 → 1.0`, opacity `0 → 1` then hold) confirms the stopped state, mirroring
    the Instagram/system idiom. On resume it fades out `dur.fast`.
  - **Haptic:** `Impact(Light)` on each toggle commit. Play and pause both tick
    (a symmetric transport toggle, like mute — not an undo).
- **Loop:** when a video reaches its end it loops seamlessly (Rust-driven
  active-item still playing) — no end-card, no flash. If looping is disabled for
  the item, the final frame holds and the center play glyph reveals to invite a
  replay.

### 2.3 Seek scrubber
The scrubber is the most physical control in the player; the thumb must feel
*attached to the finger* and notch as it passes meaningful marks.

**Idle appearance:** a 3pt track, played portion in accent, remainder at 24%
on-foreground; a 12pt-diameter thumb at the playhead.

**Touch-down on thumb (or anywhere on track):**
1. Thumb scales `1.0 → 1.6` via `spring.snappy`; the track thickens `3pt → 5pt`
   over `dur.fast` — the control "wakes up under the finger."
2. Playback **pauses** for the duration of the scrub (resumes on release if it
   was playing before). The pause is silent (scrubbing implies it).
3. **Prepare** the `Selection` generator on touch-down (zero-latency first tick).

**Dragging:**
- The thumb tracks the finger **1:1** horizontally; this is scroll-linked, never
  animated. (If a hit-target offset is needed for finger occlusion, apply a
  fixed vertical lift of the preview, not a horizontal lag.)
- The **played fill** follows the thumb exactly.
- A **preview thumbnail** (poster strip frame at the scrubbed time) floats above
  the thumb: appears on touch-down (scale `0.8 → 1.0`, `spring.bounce`), tracks
  the thumb, updates its image as Rust supplies decoded preview frames.
- **Haptic notches — `Selection`** fired when the playhead crosses:
  - each **chapter / segment boundary** if the video has them, **or**
  - otherwise the **start (0:00) and end** only. Do **not** tick every second —
    that is noise. A plain video ticks at most twice (snap-to-start,
    snap-to-end). Re-arm per crossing.
- The thumb resists slightly at exact **0:00 and end** (a 6pt rubber-band) so the
  user can feel the boundary, each paired with one `Selection`.

**Release:**
1. Seek commits to the thumb position (Rust receives the target time, requests
   the player to seek).
2. Thumb scales back `1.6 → 1.0`, track thins `5pt → 3pt` (`spring.snappy`).
3. Preview thumbnail fades + scales out (`dur.fast`, `ease.in`).
4. If the video was playing before the scrub, playback resumes; otherwise it
   stays paused at the new position.
5. No release haptic (the boundary ticks during drag already conveyed position).

**Tap on track (not drag):** the thumb animates to the tapped position via
`spring.stiff` (response 0.22 — quick, decisive), then seeks. One `Selection` on
arrival.

**Principle:** *the thumb is the finger.* 1:1 tracking, wake-on-touch, and sparse
boundary notches make seeking feel like moving a physical slider — precise,
never mushy, never chattery.

---

## 3. Buffering / stall

- If playback stalls (Rust emits `video.buffering`), a thin indeterminate
  spinner fades in over the center after a **400ms grace delay** (so brief
  stalls show nothing). Spinner: one revolution per `dur.ambient`, `linear`.
- On resume (`video.playing`), spinner fades out `dur.fast`. No haptic for
  buffering — it is a condition, not an action.
- The poster/last-good frame remains visible under the spinner; never blank to
  black on a stall.

**Principle:** *don't punish brief hiccups.* The grace delay hides the common
case; the spinner only appears when a stall is real.

---

## 4. Reduce Motion

| Normal | Reduced |
|--------|---------|
| Frame crossfade-in from poster | Poster swaps to live frame instantly (no fade). |
| Controls fade + 8pt settle | Opacity-only fade, no translate. |
| Play/pause glyph path morph | Instant glyph swap; keep the `Impact(Light)`. |
| Scrubber thumb scale / track thicken / preview pop | Thumb enlarges instantly on touch (no spring); preview appears with no scale. |
| Pause center-overlay pop | Static pause glyph, no scale. |

Haptics (`Selection` notches, `Impact(Light)` on play/pause toggle) are
unchanged under Reduce Motion.
