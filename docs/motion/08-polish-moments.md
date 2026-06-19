# Olas Polish Moments Specification

This is the specification for the moments that separate a good app from a beloved
one: empty states, errors that don't feel like errors, onboarding that feels like
a gift, and the small delights that make a person grin and not know why.

It is written to be handed directly to designers and engineers. Where a decision
could go several ways, this document **makes the call**. These are creative
decisions, not feature requirements. Read them as authoritative.

It interlocks with the rest of `docs/motion/`. It uses **only** the named tokens
defined in [`00-foundations.md`](00-foundations.md), which is the single
authoritative catalog — spring tokens (`spring.snappy`, `spring.gentle`,
`spring.bounce`, `spring.carousel`, `spring.stiff`), curve tokens
(`ease.standard`, `ease.out`, `ease.in`, `linear`), the duration scale
(`dur.instant` 80ms, `dur.fast` 140ms, `dur.base` 220ms, `dur.slow` 360ms,
`dur.deliberate` 460ms, `dur.ambient` 1200ms), and the haptic/sound tiers
(`Selection`, `Impact(Light)`, `Impact(Soft)`, `Impact(Rigid)`,
`Notify(Success)`, `Notify(Warning)`, `Notify(Error)`, `Sound(ShutterSoft)`,
`Sound(ZapChime)`). This document introduces no new tokens; if a moment needs a
value not in foundations, the value is expressed with an existing token.

## Operating principles for everything below

1. **Olas is a private Instagram for the people you love.** The emotional register
   is warmth, intimacy, and quiet confidence — never corporate, never clever for
   its own sake, never the chirpy mascot voice of a productivity app. We are the
   app your sister sends you so she can show you the baby. Write copy and design
   moments for *that* relationship.
2. **The word "nostr" appears nowhere a user can see it.** Not in onboarding, not
   in errors, not in empty states. Internally: relays, WoT, npub, Blossom. To the
   user: people, your circle, your key, photos. This is a product rule, not a
   suggestion.
3. **Never blame the user. Never expose the machine.** Errors speak in plain,
   kind language and always offer the next move. The user should never read the
   words "failed," "error," "invalid," or a status code.
4. **Earned, not sprinkled.** Delight is rationed. A celebration that fires on
   every action is wallpaper; one that fires on a genuine milestone is a memory.
   If in doubt, do less. The eleventh like is silent; the first one is a
   firework.
5. **Behavioral parity (Non-negotiable #1 from the README).** Every timing,
   curve, haptic, and copy string here ships on iOS and Android together. Copy is
   identical across platforms. Motion is the same *feel*, expressed in each
   platform's idiom.
6. **Respect Reduce Motion and Reduce Transparency.** Every blur, burst, parallax,
   and confetti below has a fallback defined inline. Honor
   `UIAccessibility.isReduceMotionEnabled` / `isReduceTransparencyEnabled` and the
   Android equivalents.

---

## 1. Onboarding feel

Onboarding is the single most important surface in Olas. Most people arrive via an
invite from someone they love. The job is not to *explain* the app — it is to make
the new arrival feel *welcomed in*, like a door opening into a warm room. Every
decision below serves that feeling over efficiency.

### 1.1 The very first screen

**What it is.** A full-bleed, slow, silent loop of real photographs — the kind of
photos Olas exists to hold. A grandmother laughing. A kid's birthday cake mid-blow.
A hiking trail. Hands holding a coffee. Ordinary, warm, *human*. Never stock-photo
glossy; these read as photos a real family took. They fade between one another at
`dur.deliberate` (460ms) on `ease.standard`, one every ~4 seconds, with an almost
imperceptible Ken Burns drift (1.0 → 1.04 scale over the full 4s, `ease.out`).

**What it feels like.** Calm. Unhurried. The opposite of a signup funnel. There is
no progress bar, no "Step 1 of 4," no skip button screaming in the corner. The
photos do the persuading.

**The copy.** One line, set large, lower-third, in the display weight (see §10):

> **The people you love, in one quiet place.**

Below it, a single primary button: **Get started**. And one quiet text link beneath:
**I have a key** (for returning users / those invited with a key).

**Motion in.** On app launch the first photo is already there (no black-frame
flash — preload the first image into the launch screen so the handoff is seamless).
Copy and button rise 12pt and fade in over `dur.slow` (360ms), `spring.gentle`,
staggered 60ms (headline first, then button). Nothing bounces. This is a calm room.

**Reduce Motion:** photos cross-dissolve without Ken Burns; copy fades with no rise.

### 1.2 The key generation moment

This is the moment that, in every other crypto app, terrifies people. In Olas it is
**invisible-but-acknowledged** — neither hidden (which feels untrustworthy) nor
ceremonial (which feels scary and technical).

**The call: functional-made-warm, not magical.** A "magical" key-gen animation
(particles coalescing into a key, etc.) over-signifies. It makes the user think
*"this is a Big Serious Crypto Thing I might screw up."* We want the opposite: the
key is treated the way a good hotel treats your room key — handed over smoothly,
with quiet reassurance that it's yours and it's safe.

**The flow.**
1. After **Get started**, the user picks a name and (optionally) a photo. This
   comes *first* so the very next screen feels like *them*, not like setup.
2. While they're typing, the key is **already being generated in the background**
   (Rust). By the time they tap **Continue**, it exists. No spinner labeled
   "generating keys."
3. The transition to the next screen is a gentle card lift. As it settles, a small,
   warm line appears under their new profile: **"Your account is yours alone."**
   That is the *only* acknowledgment most users need. The word "key" is not even
   required here.

**For the curious / cautious.** A single, low-key affordance: a small key glyph
with **"Your key"** in the corner of the profile setup. Tapping it slides up a sheet
that shows the recovery key with a friendly explainer (see §10 for the copy). This
is opt-in. Most people never tap it; the people who need it find it instantly.

**Why not magical:** magic implies something happened *to* you that you don't
understand. Olas wants you to feel that something simple and solid is now *yours*.

### 1.3 First feed load — the first photo

The first time the feed populates is the payoff of the whole invite. It must land
like a held breath releasing.

**What plays.**
- The feed arrives **already showing structure** — skeleton cards (§7) are on
  screen within `dur.instant` of the feed appearing, so there is never a blank
  white void.
- As the **first real photo** decodes, it does not pop in. It resolves: the
  blurhash placeholder (already showing, §7) sharpens into the photo over
  `dur.slow` (360ms) on `ease.out`, like a Polaroid developing but faster
  and subtler. Simultaneously the card's author row (avatar + name) fades from
  skeleton to real over `dur.base`.
- The very first time only: a one-shot, gentle `Impact(Light)` the instant the first
  photo finishes resolving. A tiny physical "there it is." This fires **once ever**,
  guarded by a first-run flag. Never again.

**No empty-feed onboarding limbo.** If the inviter's circle has content, the new
user sees a real photo within ~1s of the feed appearing. If it's slow, they see
beautiful skeletons, never a spinner alone.

**Reduce Motion:** blurhash → photo is a straight cross-dissolve at `dur.base`;
no haptic change (haptics are independent of Reduce Motion).

### 1.4 "Saved to keychain" confirmation

We never use the phrase "saved to keychain." That is machine language. The user-
facing event is: *your account is safe on this phone.*

**What it looks like.** When the account is persisted to the secure enclave /
keychain, a small **inline confirmation** settles beneath the profile, not a modal:
a soft-filled circle draws a checkmark (stroke animates over `dur.base`,
`ease.standard`), accompanied by:

> **You're all set. This stays safe on your phone.**

**What it feels like.** A `Notify(Success)` (the gentle double-tap success pattern)
exactly as the checkmark completes its stroke — motion and haptic land on the same
frame. The circle's fill blooms outward from center on `spring.gentle`. It is
reassuring, not celebratory — we save the confetti for their first *post*, not for
finishing setup. Setup completing should feel like *relief*, and relief is quiet.

The confirmation auto-advances to the feed after it's been on screen ~1.2s, or
immediately if the user taps **Continue**.

**Reduce Motion:** checkmark appears already-drawn with a fade; circle does not
bloom. Haptic unchanged.

---

## 2. Empty states

Empty-state copy, glyphs, behavior, **and** motion are specified in one place:
[`10-empty-states.md`](10-empty-states.md). This keeps every empty state — feed,
network, profile grid, notifications, search, offline — in a single canonical
file. The emotional operating principles at the top of this document still set
the tone for that copy.

---

## 3. Error states

**The principle: an error is a friend leaning over to help, not a system barking a
status code.** Every error below obeys four rules:

1. **Plain language, zero jargon.** Never "failed," "error," "invalid,"
   "exception," "relay," "Blossom," "404," "rejected," or a code. (Internally, log
   everything; surface none of it.)
2. **Always a next move.** Every error has a primary action. Dead ends are forbidden.
3. **Reassure about safety.** Whenever the user's content is at stake, the *first*
   thing we say is that it's safe.
4. **Motion: a gentle, low-amplitude shake — not a violent buzz.** The `error.shake`
   from [`06-loading-and-feedback.md`](06-loading-and-feedback.md): 3px horizontal,
   2 oscillations, `dur.base`, paired with `Notify(Warning)` (soft, single). Never
   `Impact(Rigid)` for errors — rigid is for failure-to-act, errors are recoverable.

Errors appear as **inline cards or slim toasts**, never blocking modals, unless the
user must make a choice. Toasts auto-dismiss at 4s; error cards persist until
resolved or dismissed.

### 3.1 Upload failed (Blossom server unreachable)

The user just chose a photo to share. The worst possible moment to feel like the app
ate it.

- **Surface:** inline on the composer / upload progress card. The photo thumbnail
  stays visible the entire time — *seeing* their photo still there is the reassurance.
- **Copy:** Headline **"We couldn't send that photo just yet."** Body **"It's saved
  right here — we'll try again automatically."**
- **Behavior:** **auto-retry with backoff** (Rust-owned: 2s, 8s, 30s). The progress
  ring on the thumbnail reverses into a soft "waiting" pulse during backoff. A
  **Retry now** button is available. After 3 silent failures, the copy upgrades to
  **"Still having trouble reaching the photo store."** with **Retry** and **Save as
  draft** (never lose the photo).
- **Motion:** `error.shake` on the upload card on the *first* user-visible failure
  only; subsequent auto-retries are silent (no repeated shaking — that's nagging).

### 3.2 Publish failed (relay rejected)

The photo uploaded but the post couldn't be announced to the user's circle.

- **Copy:** Headline **"Your photo's saved, but we couldn't post it yet."** Body
  **"This usually fixes itself in a moment. Want to try again?"**
- **Behavior:** auto-retry (Rust), with **Try again** primary and **Post later**
  secondary. If "post later," the post sits as a draft with a small **"Waiting to
  post"** chip and retries when connectivity/relays recover, then fires the
  first-post celebration (§4.1) *when it actually lands* — not before.
- **Never** say "rejected." Relay rejection (rate limit, policy) maps to the same
  warm copy; the *reason* is logged, not surfaced. If a relay *permanently* rejects
  (e.g. not a member of the group), escalate to: **"Looks like you're not a member
  of that group yet."** with **Ask to join** — turning a hard error into a path
  forward.

### 3.3 Profile not found (npub doesn't exist on fetched relays)

- **Copy:** Headline **"We couldn't find this person."** Body **"They may not be on
  Olas yet, or we just can't reach them right now."**
- **CTA:** **Try again** (re-fetch from more relays). The *try again* matters —
  "not found" in a relay model is often "not found *yet*."
- **Glyph:** a faded version of the §2.5 line-figure, to keep it gentle.
- **Behavior:** if the npub is structurally valid but unreachable, keep retrying
  quietly in the background; if it resolves later, replace the error in place with
  the profile, smoothly. The user might have walked away — reward them for coming
  back by having it *just work*.

### 3.4 Relay connection failed (in the relays/settings view)

This is the one place a *slightly* more technical audience lives, but still — warm.

- **Surface:** inline status row next to the relay, not a toast.
- **Copy:** status pill **"Can't connect"** in muted warning tint (amber, never
  red), with the relay's URL beneath. A **Retry** affordance on the row.
- **Visual:** each relay row carries a small status dot: green (connected), amber
  pulsing (connecting), grey (can't connect). The pulsing connecting state uses the
  slow 2.5s pulse from §2.2 — consistency signals "this is normal, we're working."
- **Behavior:** auto-reconnect with backoff; the row updates live without the user
  doing anything. Tapping **Retry** is instant gratification, not a requirement.

### 3.5 Image load failed (media server 404)

A photo in the feed whose media won't load. Common, must be graceful.

- **Visual:** the blurhash placeholder (§7) **stays** — we already have it, it's
  pleasant, and it preserves layout. Over it, centered, a small line-art "broken
  frame" glyph at low opacity and a quiet tappable label **"Tap to retry."**
- **No red. No broken-image icon.** The standard OS broken-image glyph is forbidden
  in Olas — it's the single ugliest thing a photo app can show.
- **Behavior:** tap retries the load; on success the photo resolves in with the
  normal blurhash→photo dissolve (§7). The caption, likes, and author row remain
  fully intact and interactive the whole time — a broken image must never break the
  whole post.

### 3.6 Key import rejected (invalid nsec)

The returning-user / invited-with-a-key path. The user pasted something that isn't a
valid key. They feel dumb already; do not pile on.

- **Surface:** inline under the paste field, never a modal.
- **Copy:** **"That doesn't look like a key. Double-check you copied the whole
  thing."** No "invalid," no "error." The framing is *"the text looks off,"* not
  *"you did it wrong."*
- **Motion:** `error.shake` on the input field, `Notify(Warning)`. The field's border
  warms to amber, then eases back to neutral over `dur.slow` as soon as they start
  editing — the error *forgives itself* the instant they act.
- **Smart help:** Rust validates as they type/paste. If the string is *almost* a key
  (right prefix, wrong length / bad checksum), say **"This key looks incomplete —
  make sure you copied all of it."** If it's an npub (public key) pasted where a
  private key goes, catch it specifically: **"That's a public key — you'll need your
  private key to sign in."** Specific help feels like a friend who actually looked.
- **Never echo or log the pasted secret in any user-visible or analytics surface.**

---

## 4. Delight moments

Exactly nine moments. Each is rationed, earned, and tuned so it lands as a *gift*,
not a *gimmick*. The governing test for every one: **would it still feel good the
tenth time, and does it fire rarely enough that there rarely is a tenth time?**

Global rules:
- Celebrations use `Notify(Success)` (the layered success-burst pattern) — reserved
  *exclusively* for this section, never for ordinary confirmations.
- Confetti/particle bursts honor Reduce Motion by degrading to a single soft glow
  pulse with the haptic intact.
- No celebration ever blocks input. They play *over* the UI and the user can keep
  moving. A delight you have to wait out becomes a tax.

### 4.1 First post lands

- **Trigger:** the user's *first ever* post is confirmed published (the real
  confirmation from Rust, not the optimistic local insert).
- **What plays:** the post card settles into the top of the feed on `spring.bounce`
  (a little more life than normal insertion). A warm wash of accent-tint light
  blooms from behind the card and dissipates over `dur.deliberate`. A short, soft
  confetti puff (8–12 particles, accent palette) rises from the like button.
  `Notify(Success)`. A one-line toast settles: **"Your first photo is live. Welcome
  to Olas."**
- **On the profile grid:** simultaneously, if the profile's empty-frame glyph (§2.3)
  is what they came from, the photo flies into that frame via shared-element
  transition. The blank frame *becomes* the photo.
- **Duration:** ~1.4s total, fully non-blocking.
- **Why earned:** it happens exactly once, ever. It is the threshold moment of
  becoming a member, not a user.

### 4.2 First zap received

- **Trigger:** the user receives their first zap (sats) on any post.
- **What plays:** the lightning flash from
  [`03-reactions-and-zaps.md`](03-reactions-and-zaps.md), amplified — a quick golden
  sweep across the post, the zap counter rolling up with an odometer flip,
  `Notify(Success)`. Toast: **"Someone sent you your first zap ⚡"** with the
  amount. The sender's avatar pops in on `spring.bounce`.
- **Duration:** ~1.2s.
- **Why earned:** first time only. Subsequent zaps get the *standard* (still
  lovely, smaller) zap flash — never the full celebration. The first one is a
  milestone; the rest are warmth.

### 4.3 Follow-milestone moments

- **Trigger:** crossing a **follower** milestone: 1, 10, 50, 100, 500, 1000. (We
  celebrate *being followed*, not following — being seen is the emotional event.)
- **What plays:** a gentle full-width banner descends from the top on
  `ease.standard`, soft gradient, e.g. **"10 people are following your photos 🎉"**,
  `Notify(Success)` (note: success, not the full celebrate — these recur, so they're
  dialed slightly back). It rests 2s then retracts up.
- **The first follower (milestone = 1) is special:** full `Notify(Success)`, copy
  **"You have your first follower. Someone's glad you're here."** This is the one
  that matters most in a small-community app — your first person.
- **Why earned:** milestones are logarithmically spaced, so they get *rarer* as you
  grow. You never get numb to them.

### 4.4 Pull-to-refresh "all caught up"

- **Trigger:** the user pulls to refresh and there is genuinely nothing new.
- **What plays:** instead of a sad "no new posts," the refresh spinner resolves into
  a small line-art checkmark with **"You're all caught up ✨"** that fades after 1.5s.
  `Impact(Light)`. The feed gently settles back.
- **Duration:** 1.5s, non-blocking.
- **Why earned:** turns a potential micro-disappointment (nothing new) into a small
  reward (you're on top of things). It's frequent but tiny — calibrated to *light*,
  never *celebrate*.

### 4.5 Double-tap like burst

- **Trigger:** double-tap a photo to like it (the universal photo gesture).
- **What plays:** the heart burst from `03-reactions-and-zaps.md` — heart scales up
  on `spring.bounce` past 1.0 to ~1.2 then settles, with a soft radial bloom,
  `Impact(Soft)`. On the *very first* like the user ever gives, a one-time toast:
  **"That's their day made."** Thereafter, just the burst.
- **Why earned:** the gesture is delightful *every* time because it's physical and
  immediate, but the *copy* moment is once-only so it never becomes noise.

### 4.6 Welcoming a friend you invited

- **Trigger:** someone who joined via *this user's* invite link publishes their
  first post (or completes onboarding).
- **What plays:** a notification with extra warmth — the new friend's avatar with a
  soft accent ring that draws on over `dur.base`, copy **"Maria joined because of you.
  Say hi 👋"**, with a one-tap **Welcome them** action that pre-fills a friendly
  comment/like. `Notify(Success)`.
- **Why earned:** this is *the* core loop of a private social app — bringing your
  people in. Celebrating the inviter closes the loop and makes invitation feel
  rewarding, not like unpaid marketing labor.

### 4.7 The "on this day" resurfacing

- **Trigger:** the user opens the app on a day when they posted exactly one year ago
  (or 1 month, for newer users — capped to one per day, max).
- **What plays:** a gentle card at the *top* of the feed, not a push — never
  interrupt, just offer. The old photo with **"One year ago today"**, tappable to
  view/reshare. Appears with a soft fade and a `Impact(Light)` only if it's the first
  thing they see that session.
- **Why earned:** memory is the deepest emotional hook a photo app has, and it's
  *content the user made*. It's never manufactured; if there's no anniversary photo,
  nothing shows. Scarcity is the whole point.

### 4.8 Profile photo / first profile completion

- **Trigger:** the user sets their profile photo for the first time.
- **What plays:** the new avatar settles into place with a `spring.bounce` and a
  single soft ring-bloom around it, `Notify(Success)`. No confetti — this is a
  *grooming* moment, not a milestone. The micro-reward is the satisfying settle and
  ring. Tiny, but it makes a chore feel finished.
- **Why earned:** restrained on purpose. It rewards completion without inflating a
  small action into a fake achievement.

### 4.9 Comment from someone you admire / mutual

- **Trigger:** you receive your first comment from a *mutual* follow (someone you
  follow who follows you back). This is the moment a feed becomes a conversation.
- **What plays:** the notification carries a subtle "mutual" accent — two
  interlocking rings drawing on around the avatar over `dur.base` — and copy that
  names the relationship lightly: **"James commented — you two follow each other."**
  `Notify(Success)`. The first time only.
- **Why earned:** in a small-circle app, the transition from broadcasting to
  *conversing* is the activation moment. Marking it once tells the user "this is
  what Olas is for."

**Deliberately NOT delight moments** (because over-celebrating cheapens the set):
ordinary likes after the first, every follow, posting after the first, opening the
app, scrolling milestones, every zap after the first. Confirmations there are
*functional* (a checkmark, a count tick), never celebratory.

---


## Continued in 08b

Sections 5–10 (camera/picker, context menus, skeleton placeholders, feed-type
transitions, the "new posts" pill, typography moments) live in
[`08b-surfaces-and-chrome.md`](08b-surfaces-and-chrome.md) to respect the
500-line file ceiling. They share these operating principles and the same token
catalog.

---

## Appendix: named patterns & state this spec relies on

This spec introduces **no new motion tokens** — everything composes from
[`00-foundations.md`](00-foundations.md). It does rely on two named *patterns*
(token compositions) and a set of Rust-owned state flags:

- **Error shake** — canonical failure motion. Definition lives in
  [`06-loading-and-feedback.md`](06-loading-and-feedback.md) §3: 2 cycles, ±6pt,
  `spring.snappy` segments, ~360ms with decay, paired with `Notify(Error)`. This
  doc references it; it does not redefine it.
- **Blurhash resolve** — the canonical blurhash → sharp photo dissolve:
  `dur.base` (220ms), `ease.standard`. Used in §1.3, §3.5, §7. (Matches the image
  hand-off timing in `06` §2 and `07` §1.1 — one consistent dissolve everywhere.)
- **First-run guard flags** (Rust-owned snapshot state that gates once-only
  delight; native reads, never decides): `firstFeedLoad`, `firstPost`,
  `firstZap`, `firstLikeGiven`, `firstFollower`, `firstMutualComment`.
  Native asks Rust "should this celebration fire?"; Rust owns the truth and the
  one-shot. Per Non-negotiable #2, the *whether* is Rust's; the *how* is native's.
