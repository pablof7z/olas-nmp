# 09 — Text Taps, Inputs & Settings Microinteractions

Covers: username → profile tap, hashtag tap, comment input (keyboard + send
enable), and the media-server radio selection. Tokens from `00-foundations.md`.

---

## 1. Username tap → profile

### 1.1 Haptic
- `Impact(Light)` — iOS `.light`, Android `EFFECT_TICK` — fired at touch-up,
  simultaneous with the highlight settling and the navigation starting.

### 1.2 Highlight state + duration before navigation
Finger down on a @username:
1. **T+0** (`dur.instant`, 80ms): the username text background fills with a
   subtle accent-tint pill (≈10% accent), text color shifts toward accent. This
   is the pressed state, tracking the finger.
2. **Touch-up — commit:** fire `Impact(Light)`. Hold the highlight visible for
   **exactly one frame past commit** then begin navigation — total perceptible
   highlight-before-nav is **~100ms** (the press-in 80ms + commit frame). Long
   enough to register "I hit it," short enough to feel instant.
3. Navigation push begins (`dur.slow` platform push). The highlight does **not**
   need to fade — it leaves with the outgoing view.
4. If the finger lifts **off** the text (drag-cancel), reverse the highlight over
   `dur.fast` and do nothing (no haptic, no nav).

---

## 2. Hashtag tap

### 2.1 Haptic
- `Impact(Light)` (iOS `.light` / Android `EFFECT_TICK`) at touch-up. Same tier
  as username — both are lightweight link commits.

### 2.2 Highlight
Identical mechanics to §1.2 but the destination is a hashtag feed:
1. Press-in accent-tint pill on the `#tag` text over `dur.instant`.
2. Commit → `Impact(Light)`, ~100ms highlight, then push to the tag feed.
3. The hashtag in the destination header animates in already-highlighted
   (shared-element continuity if feasible; otherwise just present).

> Both §1 and §2: do **not** use a notification haptic. Tapping a link is a
> navigation commit, not a consequence. One light tick, no more.

---

## 3. Comment input

### 3.1 Keyboard appearance
- The input bar is docked above the keyboard. When tapped, the keyboard rises
  with the **system** animation (iOS uses the keyboard frame notification curve;
  Android uses the IME WindowInsets animation). The input bar tracks the keyboard
  top **frame-locked** — it must move in perfect sync, never lag behind (use
  `keyboardLayoutGuide` on iOS, `WindowInsetsAnimationCallback` on Android).
- **No haptic** on keyboard show/hide (it is system locomotion).
- As the keyboard appears, the comment list above scrolls to keep the replied-to
  context visible (content inset animates with the same curve/duration as the
  keyboard).

### 3.2 Send-button enable animation
The send button is disabled while the field is empty/whitespace-only.
1. **First non-whitespace character typed:** the send button transitions
   disabled → enabled: it scales `0.8 → 1.0` and fades `0.4 → 1.0` opacity via
   `spring.bounce`, and its fill color animates gray → accent over `dur.fast`.
   This "pop into readiness" happens **once** on the empty→non-empty edge, not on
   every keystroke.
2. **Field returns to empty** (all text deleted): reverse via `spring.snappy`
   (scale → 0.8, fade → 0.4, color → gray). Quieter than the enable (no
   overshoot).
3. **No haptic on typing** (the keyboard supplies keyclick; see `00` §4.3).
4. **On send tap:** fire `Impact(Light)` at touch-up; the button does a press-in
   (scale 0.92, `dur.instant`) then the comment is submitted. The sent comment
   animates into the list (rise 8pt + fade, `dur.base` `ease.out`). The
   *delivery* result (relay ack) does **not** add a second haptic — comments are
   lightweight; the send tick is sufficient. (Contrast with publish/zap, which
   are heavier and earn a `Notify`.)

---

## 4. Media-server selection (Settings) — radio button feel

A list of media/blossom servers with single-selection radio controls.

### 4.1 Haptic
- **On selecting a new server:** fire `Impact(Rigid)` — iOS
  `UIImpactFeedbackGenerator(.rigid)`, Android `EFFECT_HEAVY_CLICK` (or oneShot
  12ms @ ~160/255). Rigid gives the crisp "mechanical detent" of a physical radio
  button clicking in — the premium-switch feel.
- **No haptic** when tapping the already-selected server.

### 4.2 Visual (the click feel)
Finger down on a radio row:
1. **T+0** (`dur.instant`): the whole row gets a faint pressed-tint background;
   the radio dot's outer ring scales to 0.92 (pressed-in).
2. **Commit (touch-up) — T+0:** fire `Impact(Rigid)`. The selected dot fills
   from center: inner dot scales `0 → 1.1 → 1.0` via `spring.bounce` while the
   ring color animates gray → accent over `dur.fast`.
3. **Previously-selected row:** its inner dot scales `1.0 → 0` over `dur.fast`
   `ease.in` and ring color drains to gray — **simultaneously** with step 2, so
   the selection visibly "moves" from old to new. No haptic for the deselect.
4. Reduce Motion: dot fill becomes an opacity crossfade over `dur.fast`; haptic
   unchanged.

> Why `Rigid` here but `Selection` for the WoT preset (`04` §4)? The WoT preset
> is a fast, repeatable in-context filter (selection-tier). A media-server choice
> is a deliberate, infrequent configuration commit — it deserves the firmer,
> more "settled" rigid click. Matching severity to consequence (`00` §4.2).
