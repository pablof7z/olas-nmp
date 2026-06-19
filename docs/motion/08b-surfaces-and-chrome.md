# 08b — Surfaces & Chrome (Camera, Context Menus, Skeletons, Feed Switch, Pill, Typography)

Continuation of [`08-polish-moments.md`](08-polish-moments.md). Same operating
principles, same token catalog ([`00-foundations.md`](00-foundations.md)). Split
out to respect the repository's 500-line file ceiling. This half covers the
*surfaces and chrome* moments; the parent file covers the *emotional* moments
(onboarding, empty, error, delight).

---

## 5. The camera / picker experience

The picker is opened by the center tab / **+**. It is the most-used creative surface;
it must feel like reaching for your camera, not filing a form.

**What it looks like.** A bottom sheet rises (`ease.standard`, `dur.base`) to ~92%
height, presenting a **photo grid** that fills the screen, with a **live camera
viewfinder as the first cell** in the top-left — tap it to shoot, or pick from the
roll. The sheet has a subtle top grab handle and the user's most recent photos
already visible without scrolling.

**Order of photos.** Newest first, always. The photo you want to post is almost
always the one you just took. No albums-first detour — albums are a secondary
affordance behind a small header dropdown (**"Recents ▾"**). Recents is the default
because recency is intent.

**Multi-select feel.**
- Tap a photo to select; a numbered badge (**1**, **2**, **3**…) animates in on the
  top-right of the cell on `spring.bounce` with `Impact(Light)`. The number *is* the
  order it'll appear in the post — selection order = post order, which is intuitive
  and lets the user sequence the carousel by tapping in order.
- Selected cells dim very slightly and inset 2pt (a gentle "pressed into the stack"
  feel) so selection is unmistakable at a glance.
- Deselecting renumbers the rest with a quick `dur.base` reflow — never a jarring
  jump.
- A cap (e.g. 10) is enforced gently: the 11th tap gives a soft `error.shake` on
  that cell and a slim **"Up to 10 photos per post."** toast. No hard wall feeling.

**Preview of the selection.** Yes — critical. A **selection tray** pins to the bottom
of the sheet the moment the first photo is selected: a horizontal strip of thumbnails
in order, each with a tiny **×** to remove. The tray slides up on `ease.out`.
The user can **drag to reorder** within the tray (long-press lifts the thumbnail on
`spring.gentle`, others part to make room) — so sequence is editable two ways
(tap-order or drag). The primary **Next** button lives top-right and shows the count:
**Next (3)**.

**The transition out.** Tapping **Next** carries the selection forward with a shared-
element move — the first selected thumbnail expands into the editor's main preview, so
the user's eye stays locked on *their* photo across the screen change. Continuity over
novelty.

**Reduce Motion:** sheet appears with a fade, no spring on badges, tray reorder is
instant.

---

## 6. Long press & context menus

**The blur/dim effect.** On long-press of a post, photo, or profile, the rest of the
UI **dims and blurs** behind the focused element, which **lifts toward the user**:
the target scales to 1.04 and casts a soft elevated shadow on `spring.gentle`
(`dur.base`); the backdrop blurs to ~20pt radius and dims to ~40% black, animated in
over the same duration. The target is the *only* thing in focus — this is the iOS
peek/Material "lift" idiom, held to behavioral parity. `Impact(Soft)` fires the
instant the lift commits (the long-press threshold is met), giving a physical "you've
grabbed it" confirmation.

The context menu then **springs out from the focused element** (anchored to it, not
center-screen), items cascading in with a 20ms stagger on `spring.snappy`. Menu
appears *below* the element if there's room, *above* if not, never covering the thing
you long-pressed.

**Reduce Transparency / Reduce Motion:** blur becomes a solid ~80%-opacity dim; the
lift becomes a simple selection highlight; menu fades rather than cascades.

**Menu item order** (most-likely-intended first, destructive last and visually
separated):

*On a post (someone else's):*
1. **Like** (if not already)
2. **Comment**
3. **Zap** ⚡
4. **Share**
5. **Save photo**
6. **Copy link**
7. — divider —
8. **Mute this person** (warm-destructive, amber)
9. **Report** (destructive, red, bottom)

*On your own post:*
1. **Share**
2. **Save photo**
3. **Edit caption**
4. **Copy link**
5. — divider —
6. **Delete post** (destructive, red, bottom; requires a gentle confirm — see below)

*On a profile:*
1. **Follow / Unfollow**
2. **Message** (if/when DMs exist)
3. **Share profile**
4. **Copy link**
5. — divider —
6. **Mute** (amber)
7. **Block** (red, bottom)

**Destructive confirmation.** Delete/Block don't just fire. They confirm with a small
inline sheet: **"Delete this photo? This can't be undone."** / **Delete** (red) /
**Keep**. The confirm uses `Notify(Warning)`, the destructive action `Impact(Rigid)`.
Everything else fires immediately with `Impact(Light)`.

**Dismiss.** Tapping the blurred backdrop or swiping down dismisses; the element
settles back into place on `spring.gentle`, blur clearing over `dur.base`. The menu
should feel like it *retracts into* the element it came from.

---

## 7. Skeleton placeholders

**The call: blurhash-first, branded only in absence.** Olas is a photo app; the most
beautiful, least-jarring placeholder for a photo is a low-res impression *of that
photo*. We have blurhash (or thumbhash) in the event metadata before the full image
loads — use it.

**Loading image (we have a blurhash):**
- Show the decoded blurhash immediately, full-bleed in the photo's frame. It carries
  the real photo's colors and rough composition, so the layout is correct and the
  screen is *pretty* while loading.
- Over the blurhash, a **very subtle shimmer sweep** (the skeleton shimmer from
  [`06-loading-and-feedback.md`](06-loading-and-feedback.md)): a soft diagonal
  highlight passing once every ~1.5s, low contrast, so it reads as "loading" without
  strobing.
- When the full image arrives: blurhash sharpens into the photo over `dur.slow`
  (450ms) on `ease.out` — the signature Olas resolve. Never a hard swap.

**Loading image (no blurhash available):**
- A neutral placeholder in the app's surface tint (not pure grey — a warm, slightly
  tinted neutral) with the shimmer sweep. **No logo in the placeholder cell** — a
  brand mark stamped on every loading photo would be visual noise at feed scale.

**Skeleton cards (whole feed loading):**
- Author row: a circle (avatar) + two rounded bars (name, time) in surface tint with
  shimmer.
- Photo: the tinted shimmer block at the correct aspect ratio (default 4:5 portrait —
  the most common photo crop — to minimize layout shift).
- Action row + caption: rounded bars.
- Shimmer is **synchronized across all skeleton elements** (one shared phase) so the
  whole card breathes as a unit — desynchronized shimmer looks cheap and chaotic.

**Branded loading appears in exactly one place: cold app launch.** The launch screen
shows the Olas wordmark/wave-mark, holding until the first frame of real UI is ready,
then dissolving into the feed. That is the *only* branded loading moment. Everywhere
else, the content (blurhash) is the brand.

**Reduce Motion:** shimmer is disabled; placeholders are static tinted blocks;
blurhash→photo is a cross-dissolve.

---

## 8. Transitions between feed types (Following ⇄ Network)

The two feeds live under a segmented control / top tab. Switching between them must
feel like *turning your head*, not *reloading a page*.

**The call: directional crossfade + slide, not instant swap, not full crossfade.**
- The outgoing feed slides out and the incoming slides in **horizontally**, following
  the direction of the tab moved toward (Network is right of Following → moving to
  Network slides content leftward). Travel is short — ~16pt, not a full screen-width
  push. Paired with a crossfade. `ease.standard`, `dur.base`.
- The segmented-control indicator slides under the tabs on `spring.snappy`, leading
  the content by ~40ms so the indicator feels like it *pulls* the content along.
- `Impact(Light)` on tab commit.

**Avoiding the jarring content shift.** The hard problem is that the two feeds have
different content and heights, so a naive swap janks. Mitigations:
1. **Each feed keeps its own scroll position.** Returning to Following lands you
   exactly where you left it. Switching never resets scroll.
2. **If the incoming feed isn't cached/ready,** it slides in already showing
   *skeletons* (§7), never a blank or a spinner — so the transition is always to
   *structured* content, even if the structure is placeholder.
3. The incoming feed's first screen of content is **prefetched** while the user dwells
   on the current tab (Rust keeps both warm), so the common case is an instant,
   fully-populated slide.

**Reduce Motion:** the slide is dropped; a plain `dur.base` crossfade remains. Scroll
position preservation still applies.

---

## 9. The "new posts available" pill

**What it looks like.** A compact, fully-rounded pill, centered horizontally, floating
just **below the top nav** — not touching the edge, with a soft elevation shadow so it
clearly floats above the feed. It carries a small up-arrow glyph + **"New photos"**.
When we can show them: stacked avatar fragments (2–3 tiny overlapping author avatars)
on its leading edge — **"New photos from Ana, Leo +3"** — making it social and
specific, not a generic system count. Accent-tint fill, white text, the most
*inviting* small element in the app.

**How it appears.** It does **not** exist until there's something new *and* the user
has scrolled down at least ~1 screen (if they're already at the top, new posts just
animate in — no pill needed). It then **slides down from behind the nav** on
`spring.bounce` (`dur.base`) with a subtle `Impact(Light)` — a friendly "psst, there's
more." If more posts keep arriving, the count/avatars update in place with a quick
`dur.base` cross-dissolve; the pill does not re-animate its entrance (no repeated
nagging).

**Where it sits.** Pinned below the nav, floating over the feed, centered. It stays
put as the user scrolls (it's an invitation, not feed content). It auto-hides if the
user scrolls *back* to the top on their own (they found the new posts themselves — the
pill gracefully retracts up behind the nav on `ease.out`).

**What tapping does.** A **smooth scroll-to-top** (`ease.standard`, duration scaled
to distance but capped at `dur.deliberate` so a long feed doesn't take forever),
during which the new posts are *already inserted at the top* so the user arrives into
fresh content. As the scroll completes, the newest post does a single subtle settle
(`spring.gentle`) to say "here they are." The pill retracts as the scroll begins.
`Impact(Light)` on tap.

**Reduce Motion:** pill fades in/out instead of sliding; scroll-to-top is an immediate
jump (no animated scroll) — animated long-distance scroll is a Reduce-Motion trigger.

---

## 10. Typography moments

Olas ships **two type families**: a warm, slightly humanist **display/serif** for
emotional moments (onboarding headlines, empty-state headlines, celebration copy) and
a clean, highly-legible **sans** for everything functional (captions, UI, metadata).
The contrast between them is itself a polish signal — the serif appears *only* in
moments meant to feel human and hand-written, so its presence quietly tells the user
"this part is for *you*."

**The places typography does real emotional work:**

1. **Post captions.** Set at a generous **17pt, 1.4 line-height**, sans, in near-full
   contrast (not the muted grey so many apps use for captions). A caption is the
   user's *voice*; treat it with the weight of body text in a book, not metadata.
   The author name sits at 15pt semibold; the timestamp recedes to 13pt at 60%
   opacity. The hierarchy says: *the photo, then the words, then the machinery.*

2. **The WoT explanation — reads like a friend, never a manual.** When we explain why
   a stranger's photo appears in the Network feed (tap "Why am I seeing this?"):
   > **You're seeing this because people you follow follow them.**
   > Olas shows you friends-of-friends — people close to your circle, not random
   > strangers.
   Set in the serif, conversational, second person, no jargon ("web of trust,"
   "graph," "relay" all forbidden here). It should read like your most patient friend
   explaining it over coffee.

3. **Onboarding copy that earns a smile.** The first-screen line **"The people you
   love, in one quiet place."** is set in the serif at display size with tight
   leading — it's a *statement*, not a label. The key-safety line **"Your account is
   yours alone."** uses the same serif so it inherits that warmth and reads as a
   promise, not a settings string.

4. **Empty-state headlines** (§2) all in the serif: **"This is your space."**,
   **"Your world is still small."**, **"All quiet for now."** Each is short enough to
   read as a *sigh* or a *smile*, never a sentence you have to parse. The serif makes
   blank screens feel intimate instead of broken.

5. **The recovery-key explainer** (§1.2): the *value* (the key string) is set in a
   **monospace** at comfortable size with generous letter-spacing so it's verifiable
   character-by-character — the one place mono belongs, because here the user is
   literally checking glyphs. The surrounding explanation stays in the friendly serif:
   > **This is your key. It's the only way back into your account.**
   > Keep it somewhere safe and private — like a spare house key. We can't recover it
   > for you, and we'd never want to be able to.
   The last clause ("and we'd never want to be able to") turns a scary
   non-custodial caveat into a *trust* statement. That single line is the most
   important typography moment in the app: it converts the one genuinely intimidating
   fact about owning your own keys into a reason to *trust* Olas.

6. **Numbers feel good.** Counts (likes, zaps sats, followers) use **tabular
   (monospaced) figures** so they don't shimmy when they tick, and roll on an odometer
   flip when they change. A like count that jitters as it animates is the kind of
   small ugliness that, multiplied across a feed, makes an app feel cheap. Tabular
   figures are a one-line fix that nobody consciously notices and everybody feels.

