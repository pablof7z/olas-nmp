# 10 — Empty State Animations

Covers the empty feed, empty notifications, and empty search results. Tokens from
`00-foundations.md`.

## 1. Principle

Empty states in Olas are **calm, warm, and gently alive** — not dead static art,
not attention-seeking loops. The motion is **subliminal**: a single, slow,
low-amplitude breathing loop on a simple line illustration, plus a one-time
entrance. The goal is "peaceful, intentional space," matching the private,
unhurried product feel. Nothing demands action; an inviting CTA is offered
quietly.

## 2. Shared mechanics

- **Entrance (one-time, when the empty state first resolves):** illustration +
  headline + CTA fade in and rise **10pt** over `dur.deliberate` (460ms)
  `ease.out`, staggered: illustration first, headline at +60ms, body at +120ms,
  CTA at +180ms. Plays **once** per appearance; never on every redraw.
- **Idle loop (continuous, very subtle):** the line illustration "breathes" —
  a slow scale `1.0 ↔ 1.015` over a **3.2s** period, `ease.standard` autoreverse,
  paired with an opacity drift `1.0 ↔ 0.92`. Amplitude is intentionally tiny: it
  should be felt only peripherally. One accent element may have a secondary,
  offset motion (see per-state below).
- **No haptic, no sound** on any empty state (passive — `00` §4.1, `04b` §1).
- **Reduce Motion:** drop the breathing loop entirely; keep the one-time
  entrance as an opacity-only fade (no rise). The illustration is then fully
  static.
- These are **vector line illustrations animated procedurally**, not heavy Lottie
  movies or video. Keep them on the compositor; they must cost ~0% CPU at idle.

## 3. Per-state

### 3.1 Empty feed ("Your wave is quiet")
- Illustration: a simple line-art ocean wave / horizon.
- Secondary motion: a small wave crest translates horizontally ~6pt over 4.5s
  (offset from the breathing period so they never sync — organic feel).
- CTA: "Find people to follow" / "Share your first photo" — quiet accent button.

### 3.2 Empty notifications ("All caught up")
- Illustration: a line-art bell or a calm checkmark-in-circle.
- Secondary motion: a single, slow highlight glint sweeps across the glyph once
  every ~6s (a 400ms `ease.standard` shimmer, then long pause). Signals
  "settled / done," reinforcing the haptic-free calm.
- No CTA (nothing to do is the good outcome) — just the reassuring line.

### 3.3 Empty search results ("Nothing here yet")
- Illustration: a line-art magnifier.
- Secondary motion: the magnifier drifts in a tiny slow ellipse (~4pt radius,
  5s period) — "still looking," gentle, never frantic.
- Below: contextual suggestions (recent searches / suggested tags) fade in with
  a 40ms stagger. Distinguish from the "searching" state: while a query is in
  flight, show skeletons (`06` §2), **not** this empty illustration. The empty
  illustration appears only after a resolved query returns zero results.

---

## 4. Per-state copy, glyphs & behavior

The motion above (§2 mechanics) applies to every state. This section is the
canonical home for each empty state's *illustration, copy, and product
behavior* (moved here from `08-polish-moments.md` so empty states live in one
file). The emotional operating principles in `08` still govern tone.

**House style for every empty state below:**
- **Illustration style: minimal line-art, single-weight, monochrome in the app's
  accent tint, with one small warm accent.** Not photographic (photos imply
  content that isn't there yet — cruel in an empty state), not heavy 3D
  illustration (too loud for a quiet moment). Think: a single confident line
  drawing, the way you'd doodle to a friend. Each empty state has *one* bespoke
  glyph, never a generic shrug.
- **Copy tone: warm and direct, occasionally a small smile.** Never playful to the
  point of cute, never a wall of explanation. One headline (display weight, see
  §10), one supporting line (body, secondary color), one CTA.
- **CTA is always a real next step**, never just "OK."

### 2.1 Empty Following feed (never followed anyone)

- **Glyph:** two simple line-drawn figures, a little gap between them, a small
  accent dot bridging the gap.
- **Headline:** **Your circle starts here.**
- **Body:** Follow a few people and their photos will show up right here.
- **CTA:** **Find people to follow** → opens the people search / suggested-from-
  inviter list. If the user arrived via invite, this list is pre-populated with the
  inviter and their circle, so the first follow is one tap away.
- **Behavior:** the moment they follow their first person, this state cross-fades
  (`dur.base`) to a skeleton feed, then content. The empty state never just blinks
  out — it hands off to the loading state gracefully.

### 2.2 Empty Network feed (Web-of-Trust returns nothing)

This happens to brand-new accounts with a thin social graph. It must read as
*"give it a moment, your world is small but growing"* — never as *"there is
nothing here."*

- **Glyph:** a small constellation — three or four dots, one line connecting two of
  them, room to grow.
- **Headline:** **Your world is still small.**
- **Body:** As you follow people, we'll surface photos from friends-of-friends here.
  Give it a little time.
- **CTA:** **See who's around** → same people-discovery surface.
- **Behavior:** this state is *patient*. If WoT is still computing, show the glyph
  with a single slow pulse on the connecting line (one pulse / 2.5s) to signal
  "working, not broken." Never a spinner.

### 2.3 Empty profile grid (own profile, no posts yet)

The most emotionally loaded empty state — it's *your* space, blank. Make it an
invitation, not an indictment.

- **Glyph:** a single empty photo frame, line-art, with a small **+** where the
  photo would be.
- **Headline:** **This is your space.**
- **Body:** Share your first photo and it'll live right here.
- **CTA:** **Share a photo** → opens the picker (§5) directly.
- **Behavior:** the **+** glyph gently pulses on `spring.gentle` (scale 1.0→1.06,
  every 3s) to draw the eye to the action without nagging. When they post their
  first photo, the new post animates *into the frame position* — the glyph
  literally becomes their first photo via a shared-element transition (see §4,
  first-post moment). This is one of the delight moments. It must feel like the
  blank frame was *waiting* for that photo.
- **Other people's empty profiles:** softer. Glyph + **"No photos yet."** No CTA.
  Never imply judgment of someone else's account.

### 2.4 Empty notifications

- **Glyph:** a single line-drawn bell, upright and calm (not ringing — ringing
  implies you're missing out).
- **Headline:** **All quiet for now.**
- **Body:** Likes, comments, and zaps will show up here.
- **CTA:** none. This is a resting state, not a task. A CTA here would manufacture
  anxiety. Let it be peaceful.

### 2.5 Empty search results

Two distinct cases — do not share copy.

**No people found:**
- **Glyph:** a magnifying glass over a single line-figure.
- **Headline:** **No one by that name.**
- **Body:** Double-check the spelling, or paste a key if someone shared one with you.
- **CTA:** if the query *looks like* a key (npub/nprofile heuristic in Rust),
  surface a **Go to this profile** affordance inline. Otherwise none.

**No photos found:**
- **Glyph:** a magnifying glass over an empty photo frame.
- **Headline:** **Nothing here yet.**
- **Body:** Try a different word, or follow people to see more.
- **CTA:** **Find people** (warm redirect — in a small social graph, the answer to
  "no results" is usually "follow more people").

**Behavior:** results clear and the empty state fades in only after a 350ms debounce
on the final keystroke, so the empty state never *flickers* between keystrokes. This
is critical — a flashing "no results" while typing feels broken and panicky.

### 2.6 No internet connection

The only empty state that is also an error. It must feel like *the world paused*,
not *the app broke*.

- **Glyph:** a line-drawn cloud with a gentle gap/break in its outline — soft, not
  alarming. No red. No lightning bolt. No exclamation mark.
- **Headline:** **You're offline.**
- **Body:** Your photos are safe. We'll catch you up the moment you're back.
- **CTA:** **Try again** (re-attempts connection; shows the slow line-pulse from
  §2.2 while reconnecting).
- **Critical behavior — show the cache.** If we have *any* cached feed, we do **not**
  show this full-screen state. Instead we show the cached feed with a slim,
  non-blocking banner pinned under the nav: **"You're offline — showing recent
  photos."** in muted tint. The full-screen offline state appears *only* when there
  is genuinely nothing cached to show. Olas should feel usable on the subway.
- **On reconnect:** the banner doesn't just vanish — it morphs to **"Back online"**
  in the accent tint for 1.5s with a `Impact(Light)`, then slides up and away on
  `ease.out`. A tiny "we've got you again" moment.

