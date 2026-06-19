# Olas — Feature Spec

See [`overview.md`](overview.md) for vision and principles. This document is the authoritative feature list.

---

## Feed

**F-01 Feed sources**
Two sources, switchable via a pill control at the top of the home screen:
- **Following** — reverse-chronological posts from accounts you explicitly follow.
- **Network** — reverse-chronological posts from the wider trust graph, filtered by WoT score. Default for new users (who have no follows yet); automatically becomes secondary once user follows ≥ 15 accounts.

Decision: chronological within each source. WoT controls *inclusion*, not *ordering*.

**F-02 Media-only feed**
Only events containing at least one image or video render in the feed. Pure-text notes are filtered out silently. Kind 20 picture events are the native format; kind-1 notes with `imeta` tags render as photos for read compatibility.

**F-03 Inline engagement**
Every feed card surfaces: react (♥), comment (💬), zap (⚡), share (↗), overflow (⋯). No secondary screen required for any of these.

**F-04 Pull-to-refresh**
Custom branded animation above the feed. Newer events prepend silently; a "N new posts" pill appears at the top rather than auto-jumping the scroll position. Tapping the pill smooth-scrolls to top and reveals new content.

**F-05 Feed card anatomy**
Full-bleed image (no horizontal margin). Below image: action row, then reaction summary ("47 reactions"), then username-caption line (username bold, caption inline or below), then comment preview ("View 12 comments"), then timestamp. No like count shown as a raw number — always "N reactions" to reduce metric pressure.

**F-06 Carousel posts**
Multi-image posts display as a swipeable carousel within the feed card. A dot indicator shows position. All images in a carousel must be uploaded before the event publishes.

**F-07 Video autoplay**
Muted, in-feed, Wi-Fi only by default. Settings: Always / Wi-Fi only / Never. Volume unmutes on tap. Videos loop.

**F-08 Mute & report**
Available from the ⋯ overflow on any card. Mutes sync via NIP-51 mute list (kind 10000) across devices. Reports are locally immediate (post disappears) and emit a report event where the relay supports it.

**F-09 Content warnings**
Posts tagged with NIP-36 content warning display a blur overlay with "Tap to view — Sensitive content." Settings: Always blur / Ask / Never blur.

**F-10 Deduplication**
Same event received from multiple relays renders once, keyed by event ID. Same media hash appearing in different events renders separately (they are genuinely different posts).

---

## Publishing

**P-01 Media picker**
Opens to native camera roll, most-recent first. Multi-select up to 10 items. Inline camera capture shortcut. Videos show duration badge.

**P-02 Crop & aspect**
Per-image crop with aspect presets: 1:1 (square), 4:5 (portrait, default), 16:9 (landscape), original. Free-form crop available.

**P-03 Filters**
12 named filters + Original. Applied client-side (GPU, native layer) before upload. Filter-applied version is what uploads — original never sent unless chosen. Each filter has an intensity slider (0–100, default 75). Filters are identical in output on iOS and Android; the filter pipeline is specced in [`docs/design/filters.md`](filters.md).

Filter names: Daylight, Ember, Dusk, Mist, Chrome, Film, Fade, Grain, Arctic, Copper, Veil, Bloom.

**P-04 Adjustments (advanced)**
Exposed via an "Adjust" tab alongside the filter carousel: Brightness, Contrast, Saturation, Warmth, Sharpen, Vignette, Fade. Each is a slider. "Advanced" label in the composer makes this feel optional.

**P-05 Caption**
Plain text with hashtag (#) and @mention autocomplete. Mentions resolve to display names backed by NIP-05 / profile search. Caption is optional.

**P-06 Alt text**
Per-image, prompted but skippable. A persistent "Add alt text" chip sits on each image in the compose carousel. Remembered preference: "Always ask / Never ask / Ask once."

**P-07 Upload pipeline**
1. Hash all media (SHA-256) locally.
2. Upload to primary Blossom server; mirror to secondary servers in parallel if configured.
3. Generate `imeta` tags: `url`, `x` (hash), `m` (MIME type), `dim` (WxH), `blurhash`, `alt`.
4. Construct kind-20 event with all `imeta` tags.
5. Sign event.
6. Publish to all write relays.

Steps 1–3 happen behind the "Share" tap. The post appears optimistically in the feed immediately after step 5 (sign); steps 6+ reconcile in the background.

**P-08 Optimistic posting**
The composed post appears at the top of the Following feed the instant it is signed, before relays confirm. A subtle spinner on the post card tracks relay delivery. If all relays fail, the card shows a "Tap to retry" state. The draft is never discarded.

**P-09 Drafts**
Locally saved to device. Never auto-published. Accessible from the compose entry point as "Continue editing."

**P-10 Video trimming**
Single-clip trim only: scrubable filmstrip with draggable start/end handles. Cover frame selection (scrub to pick). No multi-clip editing.

**P-11 Video compression**
Auto-compress before upload to a reasonable bitrate (configurable in advanced settings). A real percentage progress bar during upload. Upload continues in background with a system notification on completion.

**P-12 Target selection (power user)**
Under "Advanced" in the composer: which relays to publish to, which Blossom server to use for this post. Hidden by default, populated from global settings.

**P-13 Deletion**
Issues NIP-09 deletion request. Shows the explicit caveat: "Relays may not honor this immediately, and copies may exist elsewhere." Post hides locally immediately on tap.

---

## Media Servers (Blossom)

**MS-01 Default server**
`blossom.primal.net` pre-configured for all new users.

**MS-02 Server list**
Users manage a ranked list of Blossom servers (kind 10063). First server is primary (uploads go here first); remaining are mirrors.

**MS-03 Mirroring**
After primary upload succeeds, mirror to all other configured servers. Mirroring is best-effort — primary success is the only hard requirement for publish to proceed.

**MS-04 Fallback on read**
When fetching an image by URL, if the primary URL 404s, attempt all other known servers for the same hash. This is transparent to the user.

**MS-05 Hash verification**
Every fetched blob is verified against the SHA-256 `x` tag before rendering. Tampered or mismatched blobs are rejected silently and the next server tried.

**MS-06 Auth**
Blossom uploads authorized via signed kind-24242 auth events. Signing uses the active signer (local keystore, NIP-46, or NIP-55).

**MS-07 Server health UI**
Each server in settings shows: Connected / Slow / Down, and a latency figure. Last-checked timestamp visible.

**MS-08 Storage browser**
List of blobs the user has uploaded (by hash), with size, date, and a "Remove from server" action. Paginated. Power-user feature under Advanced in settings.

**MS-09 Re-upload / repair**
If a post's media is unreachable on all servers and the user still has the original on device, an offer to re-upload appears on the post (in own profile grid).

---

## Web of Trust (WoT)

Full spec in [`docs/spec/wot.md`](wot.md). Summary for this feature list:

**WoT-01 Graph computation**
Built from kind-3 (follow lists) and kind-10000 (mute lists) via `nmp-wot`. Score per author pair. Computed in Rust, cached in-session.

**WoT-02 Trust threshold**
User-facing control: three presets (Close / Balanced / Open) mapped to numeric thresholds internally. Balanced is the default. A raw slider lives under Advanced. Per-surface overrides (Feed vs. Notifications).

**WoT-03 WoT-on by default**
Network feed, Discover, and Notifications all filter by WoT. Toggle to "Show everyone" is one tap in each surface.

**WoT-04 Follows always bypass WoT**
Directly-followed accounts are always shown regardless of score.

**WoT-05 New user fallback**
Before the user has built a graph, feed seeds from curated starter packs. Never an empty feed.

**WoT-06 Trust transparency**
Tapping the WoT indicator on a profile shows: "Followed by Ana, Leo, and 3 others you follow." No score numbers — human framing only.

**WoT-07 Mute propagation**
Muting an account reduces trust contribution of content associated with them. Mute lists from people you follow are used as trust-reducing signals (soft penalty, not hard block unless you personally muted).

---

## Profile

**PR-01 Header**
Avatar (circular), banner image (16:9), display name, handle/NIP-05, bio (up to 300 chars), website link, Lightning address. Edit available from own profile.

**PR-02 Stats**
Following count, follower count (best-effort, computed from observed events). No post count displayed (deemphasizes volume metrics).

**PR-03 Post grid**
3-column square grid of the user's picture posts, newest first. Tap opens post in full.

**PR-04 WoT trust line (others only)**
Below the header: "Followed by Ana, Leo, and 4 others you follow" or "Outside your network." Hidden if you are the profile owner.

**PR-05 Actions (others)**
Primary: Follow / Following button. Secondary: Zap. Overflow: Mute, Report, Copy link, Add to list.

**PR-06 Propagation on edit**
Saving profile changes publishes a replaceable kind-0 event to all write relays. UI confirms only after at least one relay accepts. Re-broadcasts to all write relays on each save.

---

## Notifications

**N-01 Types**
Reactions, comments, @mentions, new followers, reposts, zaps — grouped by type and time.

**N-02 WoT filter**
Stricter WoT threshold than feed by default. Notifications from outside the trusted network are silenced (not deleted — accessible via "Show all").

**N-03 Grouping**
"Ana and 11 others reacted to your photo." Tapping expands the full list.

**N-04 Tabs**
All / Mentions / Zaps.

**N-05 Push notifications**
In-app only for v1. Background fetch wakes the app to check; local notifications fire when new activity is found. Server push (via a push relay) is an opt-in fast-follow, not a v1 dependency.

---

## Relay Views (Magazine)

**RV-01 Per-relay browse**
Select any relay (from your list or ad-hoc URL entry) and see picture events flowing through it.

**RV-02 Layouts**
Magazine (editorial grid, varied tile sizes, hero images) and Grid (uniform, dense). User toggles; default is Magazine.

**RV-03 Relay header**
Name, icon, description, software, posting policy (from NIP-11). Connection status.

**RV-04 WoT toggle**
WoT-on by default in relay view. One-tap "Show everything on this relay" surfaces unfiltered content — visually labeled as unfiltered.

**RV-05 Relay directory**
A curated list of photo-friendly relays for discovery, without requiring the user to add them permanently.

**RV-06 Jump from post**
From any post in any feed, "Open on relay" navigates to the relay view for the relay that served this post.

---

## Settings (progressive disclosure)

**Tier 1 — Always visible**
Account · Notifications · Content & filtering · Appearance · Help

**Tier 2 — Under "Advanced" (self-reveals for power users)**
Servers (Blossom) · Relays · Wallet & zaps · Account security

**Tier 3 — Inside each Advanced screen, "Manage manually"**
Raw relay URLs, read/write flags, per-relay health, mirror configs, WoT threshold slider, key export, signer management.

Every Tier-3 screen has a "Reset to recommended" action. No Tier-3 state can permanently break the app.

**S-01 Account** — Edit profile, NIP-05 verification, key backup, logout.
**S-02 Content & filtering** — WoT preset, sensitive content handling, muted accounts.
**S-03 Notifications** — Per-type toggles, WoT strictness, push opt-in.
**S-04 Appearance** — Light / Dark / System, video autoplay, data saver, magazine density.
**S-05 Servers (Adv.)** — Blossom server list, health, mirroring, storage browser.
**S-06 Relays (Adv.)** — Read/write relay list, add/remove, per-relay test.
**S-07 Wallet (Adv.)** — NWC connection, default zap amounts, zap comments.
**S-08 Account security (Adv.)** — Recovery key backup/export, signer type (local / NIP-46 / NIP-55).

---

## Search & Discovery

**SD-01 Search**
Tabbed: People / Photos / Tags. People results ranked by trust proximity. Photo results by recency within the WoT-filtered network.

**SD-02 Suggested accounts**
On the Search tab's default state: accounts followed by people you follow, ranked by follow-overlap count, each with a social-proof line ("Followed by Ana + 3 others").

**SD-03 Starter packs**
Curated sets of accounts for new users to bootstrap their graph. Categorized (Photography, Food, Travel, etc.). Used in onboarding follow step and accessible from Search.

**SD-04 Hashtag feeds**
Tapping a hashtag in a caption opens a filtered feed of that tag's picture events, WoT-filtered.
