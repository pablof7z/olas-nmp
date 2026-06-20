# Olas Onboarding Flow — Design Document

> Status: Design / not yet implemented. Authoritative for the next onboarding work cycle.
> Owner: Pablo (pablof7z). Drafted 2026-06-20.
> Scope: full onboarding, with depth on the **real-follow-pack** selection step.

This document specifies the end-to-end Olas onboarding experience and, in depth, the
follow-pack selection step. The single hard requirement: the follow-pack step must show
**real follow packs and real media follow packs fetched from the live network** — the
current hardcoded placeholders (Visual Storytellers, World Travelers, Digital Artists,
Food & Culture, Nostr Builders; and the Android STARTER_PACKS list) are **removed**.

All design choices respect the repo's hard rules (`AGENTS.md`):

- **Rust (the NMP kernel) owns all business logic.** Swift/Kotlin render UI and call
  capabilities through the NMP bridge. No parsing, no signing, no filtering, no protocol
  knowledge in the native layers.
- **No protocol vocabulary is ever shown to users** — no "Nostr", "relay", "pubkey", "npub",
  "kind", "WoT", "follow pack" event-id, etc. Users see "packs", "creators", "people".
- **Trust is shown socially**: "Followed by Ana + 3 others", never numeric scores or keys.
- **iOS / Android parity is mandatory.** Every native change lands on both platforms in lockstep.
- File-size limits: 300 LOC (view files) / 500 LOC (others).

---

## 1. Verified protocol facts (the technical anchor)

These are the **real** event kinds, verified against the NIP-51 source. The previous
design's claim of "kind 30000 follow packs" was imprecise: kind 30000 is the generic
*Follow set*; the dedicated, shareable **pack** kinds are 39089 and 39092.

| Concept (user-facing) | Real kind | NIP-51 name | Spec text |
|---|---|---|---|
| **Follow pack** (general) | **39089** | Starter packs | "a named set of profiles to be shared around with the goal of being followed together" |
| **Media follow pack** (photographers / visual creators — most relevant to Olas) | **39092** | Media starter packs | "same as above, but specific to multimedia (photos, short video) clients" |
| Follow set (generic, *not* used for onboarding packs) | 30000 | Follow sets | "categorized groups of users a client may choose to check out in different circumstances" |

Source: NIP-51, `nostr-protocol/nips/51.md`
(https://github.com/nostr-protocol/nips/blob/master/51.md).

### Pack structure (addressable / parameterized-replaceable events)

Kinds 39089 and 39092 are **addressable** (parameterized-replaceable) events. Their identity is
the coordinate `kind:pubkey:d-tag` (rendered to users never; addressed internally as `naddr`).

- `d` tag — stable set identifier (lets a curator update a pack in place).
- `title` tag — human pack name. *(Sets "can optionally have a `title`, an `image` and a
  `description` tag that can be used to enhance their UI." — NIP-51 Sets section.)*
- `image` tag — cover image URL.
- `description` tag — one-line blurb.
- `p` tags — one per member pubkey; the people you follow when you pick the pack.

Updateable in place: republishing with the same `kind`, `pubkey`, `d` and a newer
`created_at` replaces the pack. We always keep the newest per coordinate.

### Discovery on the network

- Packs are ordinary events; we discover them by subscribing with a filter on
  `kinds: [39089, 39092]` (optionally constrained by `authors` for our curated set).
- Curators publish to general relays; aggregator/index relays (e.g. nostr.band-class
  indexers) make `kinds: [39089, 39092]` queries practical for "popular packs". The kernel's
  existing relay set + outbox handling resolves where to read.
- Member profiles (kind 0) for avatar/name previews are fetched by the kernel as a
  follow-on interest keyed off the `p` tags — the native layer never issues these queries.
- Real-world precedent for the pack concept and discovery UX:
  callebtc/following.space (https://github.com/callebtc/following.space) — "create and
  explore follow packs for nostr"; Primal and Nostur ship starter-pack discovery built on
  the same 39089/39092 kinds.

---

## 2. Curation / source strategy (DECIDED)

**Hybrid: Olas featured packs + live network.** This is the chosen strategy (resolves the
documented open question "who publishes the kind events Olas shows").

1. **Featured tier (primary).** Featured packs are published from **pablof7z's own account
   pubkey** for now (resolve the real hex/npub during implementation). The kernel reads this
   from a single named constant — `FEATURED_CURATOR_PUBKEY`, currently set to pablof7z's
   pubkey — so a dedicated `OLAS_CURATOR_PUBKEY` can be formalized later by swapping one value.
   It publishes a small set of hand-picked **media follow packs (39092)** plus a few general
   **follow packs (39089)**, guaranteeing quality, on-brand cover art, and a non-empty first
   screen on day one. The discovery interest is
   `kinds: [39089, 39092], authors: [<pablof7z pubkey>]`.
2. **Network tier (shipped at launch, alongside featured).** In parallel the kernel opens a
   broader interest (`kinds: [39089, 39092]` without an author constraint, limited) to surface
   popular community packs. These rank **below** featured packs in the single mixed list.
3. **Fallback (network returns nothing — offline / cold relays).** The kernel embeds a tiny
   **static manifest of featured pack coordinates** (naddrs only, ~6 entries, no member data).
   The UI shows these as cards in a "couldn't reach the network" state with a Retry affordance;
   picking one still works once a member fetch completes, otherwise the step is skippable.

Rationale: featured-first gives a polished, deterministic first run (critical for an
Instagram-class onboarding) while the live network tier keeps the catalog fresh and
decentralized from day one. The static fallback guarantees the step never dead-ends on a bad
network.

**Ordering / interleaving rule (final), for the single mixed screen:** the screen is one
continuous, visually mixed list — **not** two hard-separated sections or steps. Ranking is a
stable sort with three keys, applied in order:
1. **media (39092) before general (39089)** — Olas is a photo app, visual-creator packs lead;
2. **featured (pablof7z) before network** — curated quality first;
3. **member count descending** — bigger, more-established packs above smaller ones.

So featured media packs appear at the very top, followed by featured general packs, then
network media packs, then network general packs — but they flow as one scroll with no section
dividers. A subtle "Featured" chip may mark curated cards; the boundary is never a visible
section break.

---

## 3. End-to-end flow

Final sequence (refines the current Splash → Welcome → Create → Follow packs →
Media server → All set). Two locked refinements:

- **One follow-pack step (DECIDED).** A single follow-pack screen presents **one continuous,
  visually mixed list** — media follow packs (39092) on top, general follow packs (39089)
  below — rather than two hard-separated steps or two divided sections. This is faster, reads
  better, and keeps the photo-first intent front-and-center.
- **Follow packs before Media server (DECIDED).** Packs come early so the feed is seeded by the
  time onboarding ends; media-server selection stays late (it only matters at first upload).
  The canonical Rust `olas_onboarding_steps_json()` array must list **one** `follow_packs` step
  placed **before** `media_server` (it currently lists `media_server` first — swap them; Rust is
  the source of truth for step order).

```
 Splash ─▶ Welcome ─▶ Create account ─▶ Follow packs ─▶ Media server ─▶ All set!
 (0.8s)    (CTAs)     (name+username,    (REAL 39092+    (Blossom        (feed ready)
                       silent keypair)    39089 packs)    default)
```

Goals & tone per screen:

- **Splash** — brand moment, 0.8s auto-advance. No copy beyond logo.
- **Welcome** — warm, confident, zero jargon. "Create account" (primary) / "I already have
  an account" (secondary). Background photo mosaic.
- **Create account** — display name + `username` with `@` prefix and `.olas.app` suffix baked
  into the field. **Keypair is generated silently in the kernel and never shown.** Copy avoids
  "key/seed/recovery". (NIP-05 registration for `username@olas.app` is deferred — field shows
  the suffix, backend wiring later.)
- **Follow packs** — the heart; section 4.
- **Media server** — "Where your photos live." Blossom default pre-selected
  (`https://blossom.primal.net`); single tap to continue. Framed as storage, not protocol.
- **All set!** — celebratory, drops the user into a pre-seeded feed.

Auto-account model: on Create, native calls `createAccount(name, username)` → Rust
`olas_create_account` builds the kind-0 profile (`{name, nip05: username@olas.app}`),
generates the keypair, makes it active. The native layer never sees key material.

---

## 4. The follow-pack selection step (core)

### 4.1 Goals

Get the user to ≥15 follows in 1–2 taps using **real, curated, media-first** packs, so that
the feed auto-switches Network → Following and looks alive immediately.

### 4.2 Data: how real packs reach the UI

1. On entering the step, native calls `olas_open_follow_pack_discovery(app)`.
2. Rust opens the **featured** interest
   (`kinds:[39089,39092], authors:[FEATURED_CURATOR_PUBKEY]` — pablof7z's pubkey) and the
   **network** interest (`kinds:[39089,39092], limit:N`), plus a member-profile follow-on
   interest for avatar previews.
3. As pack events arrive, Rust decodes each into a **UI-ready wire struct** (no protocol
   fields) and the native layer polls/observes via `olas_follow_packs_snapshot_json(app)`.
4. Rust ranks per section 2 ordering and de-duplicates members across packs.

**Wire struct (Rust → native JSON). Contains zero protocol vocabulary:**

```json
{
  "packs": [
    {
      "id": "opaque-stable-handle",        // internal naddr handle; never displayed
      "kind_group": "media",               // "media" (39092) | "general" (39089)
      "featured": true,
      "title": "Street Photographers",     // from title tag
      "cover_image_url": "https://…",      // from image tag
      "description": "Raw, candid city life", // from description tag
      "member_count": 28,                  // count of p tags (plain-language in UI)
      "preview_avatars": [                 // up to 6, profiles the kernel resolved
        { "image_url": "https://…", "display_name": "Ana" }
      ],
      "social_proof": {                    // computed by kernel from the user's graph
        "names": ["Ana", "Marco"],         // people the user already follows who are in/near this pack
        "extra_count": 3                   // → "Followed by Ana, Marco + 3 others"
      },
      "default_selected": true             // kernel marks recommended defaults
    }
  ],
  "state": "loading" | "ready" | "empty_offline",
  "selection_summary": { "pack_count": 0, "people_count": 0 }
}
```

The native layer renders this verbatim. It does **not** know what a `p` tag or a kind is.

### 4.3 Presentation

- **One screen, one continuous mixed list (DECIDED).** No section dividers: media packs (39092)
  sort to the top, general packs (39089) below, per the section-2 ranking. Featured (pablof7z)
  cards may carry a small "Featured" chip but are not separated into their own block. Media-first
  ordering is the whole point.
- **Cards** show: cover image, human title, description, "Followed by Ana + 3 others" social
  proof (only when `social_proof.names` non-empty; for a brand-new account with no follows yet,
  fall back to overlapping member avatars + "28 people"), member-preview avatars (≤6,
  overlapped), member count as plain language ("28 people", never "28 pubkeys").
- **Multi-select**, large tap target, checkmark on the whole card (iOS uses circular
  check, Android uses the existing card-border + check to match platform idiom; switches
  are acceptable on Android per current code).
- **Default selection:** the kernel pre-selects 1–2 featured media packs
  (`default_selected: true`) so a single "Continue" tap already yields a good feed.
- **How many:** show up to ~8 cards total (e.g., 4 media + 4 general). Lazy-load more on scroll
  only if the network tier returns extras; do not overwhelm.

### 4.4 States

- **Loading** — skeleton cards (cover-image shimmer + 2 text lines + avatar dots). Header copy
  stays. `state: "loading"`.
- **Ready** — cards as above.
- **Empty / offline** (`state: "empty_offline"`) — show the static-manifest fallback cards if
  available; otherwise a friendly "We couldn't load suggestions right now" with **Retry** and a
  prominent **Skip** that still completes onboarding (feed defaults to Network).
- **Slow relays** — featured cards render the instant they arrive; network cards stream in
  below without reflowing the user's current scroll.

### 4.5 On confirm

1. Native calls `olas_apply_selected_follow_packs(app, ids_json)` with the selected opaque ids.
2. Rust expands each selected pack's `p` tags, **unions** them, **removes duplicates** and
   **removes the user's own pubkey**, merges with any existing contact list, and publishes a
   single updated **kind:3 contact list** (one event, not one-per-follow). This replaces the
   current N× `nmp.follow` dispatch loop in `FollowPacksView.swift`, which is both chatty and
   pushes member-expansion logic toward the edge.
3. Rust reports the resulting follow count; when it crosses **≥15**, the kernel flips the feed
   default from **Network** (WoT-filtered global) to **Following**, seeding a populated feed by
   the time "All set!" appears.

### 4.6 ASCII wireframes

**Follow-packs (ready):**
```
┌─────────────────────────────────────┐
│            ● ● ○   (progress)        │
│   Follow some people                 │
│   Get a great feed right away        │
│                                      │   ← one continuous mixed list,
│  ┌────────────────────────────────┐  │     no section dividers
│  │ ░░░░ cover image ░░░░ ★Featured │  │   ← featured media pack (top)
│  │ Street Photographers       [✓] │  │
│  │ Raw, candid city life          │  │
│  │ (◔)(◔)(◔)(◔)(◔)(◔)  28 people  │  │
│  │ Followed by Ana + 3 others     │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ Film & Analog        ★Featured │  │   ← featured media pack
│  │                          [   ] │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ City Nightscapes         [   ] │  │   ← network media pack
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ Open Web Builders        [   ] │  │   ← general pack (39089), below media
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ Travel & Places          [   ] │  │ ← network, streams in
│  └────────────────────────────────┘  │
│──────────────────────────────────────│
│  2 packs · 54 people                 │
│  [        Continue         ]         │  ← "Skip" when 0 selected
└─────────────────────────────────────┘
```

**Loading (skeleton):**
```
┌─────────────────────────────────────┐
│   Follow some people                 │
│  ┌────────────────────────────────┐  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓         │  │
│  │ ▓▓▓▓▓▓▓▓▓▓                     │  │
│  │ ▓▓▓▓▓▓                         │  │
│  │ (○)(○)(○)(○)                   │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓ (shimmer) …      │  │
│  └────────────────────────────────┘  │
└─────────────────────────────────────┘
```

**Empty / offline:**
```
┌─────────────────────────────────────┐
│   Follow some people                 │
│                                      │
│        (cloud-slash icon)            │
│   We couldn't load suggestions       │
│   right now.                         │
│                                      │
│        [   Try again   ]             │
│                                      │
│──────────────────────────────────────│
│  [          Skip           ]         │
└─────────────────────────────────────┘
```

---

## 5. Rust / Swift / Kotlin split & FFI signatures

All new logic is Rust. Native renders the wire struct and forwards taps. Signatures follow
the existing `olas_*` C-ABI style (see `apps/olas/nmp-app-olas/src/app.rs`,
`extras_ffi.rs`). Strings returned must be freed with `nmp_free_string`.

### New Rust capabilities

```c
// Open the follow-pack discovery interests (featured + network + member profiles).
// Idempotent; safe to call on each entry to the step.
void   olas_open_follow_pack_discovery(void* app);

// Close the discovery interests when leaving the step (free relay budget).
void   olas_close_follow_pack_discovery(void* app);

// Current decoded, ranked, de-duplicated packs as the section-4.2 wire JSON.
// Returns "{...,\"state\":\"loading|ready|empty_offline\",...}". Free with nmp_free_string.
char*  olas_follow_packs_snapshot_json(void* app);

// Apply the user's selection: expand p-tags, union, drop dupes + self, merge with
// existing contacts, publish ONE kind:3. ids_json = ["<opaque id>", ...].
// Returns {"follow_count": <int>, "feed_default": "network"|"following"}.
char*  olas_apply_selected_follow_packs(void* app, const char* ids_json);
```

### Existing capabilities reused

- `olas_create_account(app, name, username)` — silent account creation (already present).
- `olas_onboarding_steps_json()` — canonical step order. Update the array to a **single**
  `follow_packs` step placed **before** `media_server` (it currently lists `media_server`
  first; swap them).
- `olas_open_follow_pack(app, pack_addr)` — keep for deep-linking into a single pack from
  outside onboarding; **not** used for the discovery list.
- Existing kind:3 / contact-list handling (`olas_contact_list_pubkeys_json`) for read-back.

### Native responsibilities (iOS + Android, identical)

- Call `olas_open_follow_pack_discovery` on appear, `..._close...` on disappear.
- Poll/observe `olas_follow_packs_snapshot_json`, decode the wire struct, render cards,
  skeletons, and the offline state.
- Track selected opaque ids in view state; render `selection_summary` as
  "N packs · M people".
- On Continue: call `olas_apply_selected_follow_packs(ids_json)`; advance.

### What native MUST NOT do (removals)

- **Delete** `FollowPack.defaults` (iOS `Models.swift`) and `STARTER_PACKS`
  (Android `FollowPacksScreen.kt`) — all hardcoded packs.
- **Remove** the per-member `nmp.follow` loop in `FollowPacksView.swift`
  (`followSelectedPacks()`); member expansion + kind:3 publish moves to Rust.
- **Remove** `previewAvatars = pravatar.cc` placeholder avatars; avatars come from the
  kernel-resolved `preview_avatars`.

---

## 6. Edge cases & states

| Case | Behavior |
|---|---|
| No network | `state: "empty_offline"` → static-manifest cards if any, else Retry + Skip. Onboarding always completable. |
| Slow relays | Featured cards render on arrival; network cards stream below without scroll jump. Skeletons until first event. |
| Duplicate members across packs | Rust unions + de-dupes before publishing kind:3; "M people" counts uniques. |
| A pack member is the user | Rust drops the user's own pubkey from the follow set. |
| Re-running onboarding | New follows **merge** with existing kind:3 (never replace-clobber). Already-followed members are no-ops. |
| Skipping the step | Allowed; no follows added; feed stays Network (WoT-filtered) until ≥15 follows. |
| Pack with broken/missing cover image | Card renders with a generated color/gradient placeholder + title; never blank. |
| Pack with <N resolvable profiles | Show available avatars; "M people" from `p`-tag count, not from resolved-profile count. |
| Selection then back-navigation | Selection state held in native view-model until Continue; nothing published until confirm. |

---

## 7. Implementation plan (parity-aware, Rust-first)

1. **Rust — discovery & wire model.**
   - Add `FEATURED_CURATOR_PUBKEY` constant (set to pablof7z's real pubkey — resolve hex/npub
     during implementation) + static fallback manifest (≤6 naddrs).
   - Implement `olas_open_follow_pack_discovery` / `olas_close_follow_pack_discovery`
     (featured + network + member-profile interests, both tiers shipped).
   - Implement pack-event decode → wire struct; ranking (media-first, featured-first,
     count desc) producing one continuous list; member-profile resolution for
     `preview_avatars`; `social_proof` from the user's graph.
   - Implement `olas_follow_packs_snapshot_json` (incl. `state` computation).
   - Implement `olas_apply_selected_follow_packs` (union, de-dupe, drop self, merge, one kind:3,
     return follow_count + feed_default; flip Network→Following at ≥15).
   - Update `olas_onboarding_steps_json` ordering (`follow_packs` before `media_server`).
   - Unit-test decode/ranking/de-dupe/self-removal in Rust.
2. **Bridge headers.** Add the four new prototypes to `apps/ios/Olas/Bridge/olas_app.h` and the
   JNI exports (`jni_*` in `apps/olas/nmp-app-olas/src/`).
3. **iOS.** Rewrite `FollowPacksView.swift` to render the wire struct (sections, skeleton,
   offline, social proof, cover images); delete `FollowPack.defaults`; replace
   `followSelectedPacks()` with `olas_apply_selected_follow_packs`. Keep view ≤300 LOC
   (extract `FollowPackCard` if needed).
4. **Android (lockstep).** Rewrite `FollowPacksScreen.kt` identically; delete `STARTER_PACKS`;
   wire `OnboardingViewModel` to the new bridge calls; mirror sections/states/copy exactly.
5. **Parity + size check.** Confirm both platforms render identical states/copy and obey
   300/500 LOC limits.
6. **PERT Test gate.** iOS simulator (xcode-build-orchestrator) + Android emulator: enter
   onboarding, confirm real packs load, select, Continue, verify feed seeded and
   Network→Following switch at ≥15. Screenshots to the PR. Both platforms PASS before review-ready.

---

## 8. Decisions & remaining open questions

### Decided (owner-confirmed, locked into this design)

1. **Step layout — ONE screen, media on top.** Single follow-pack step; one continuous,
   visually mixed list — media follow packs (39092) first, general follow packs (39089) below.
   No two-step / two-section split. Rust `olas_onboarding_steps_json()` has a single
   `follow_packs` step placed **before** `media_server`.
2. **Source mix — Featured + live network.** Both tiers ship: pablof7z-published featured packs
   first, then a live `kinds:[39089,39092]` network query for popular community packs ranked
   below. Offline static-manifest fallback retained.
3. **Curator identity — pablof7z for now.** Featured packs are published from pablof7z's own
   account pubkey, read via the `FEATURED_CURATOR_PUBKEY` constant. A dedicated
   `OLAS_CURATOR_PUBKEY` can be formalized later by swapping that one value.

### New second-order open questions (created by the above)

These need the owner's input before implementation:

1. **Featured ↔ network interleaving fairness.** The locked sort puts *all* featured cards above
   *all* network cards within each kind group. Is strict "featured-first" desired, or should a
   couple of high-signal network packs be allowed to interleave above weaker featured ones
   (e.g. featured floor of N, then merge by member count)? Current design = strict featured-first.
2. **Network-tier moderation.** Shipping an unconstrained `kinds:[39089,39092]` query exposes the
   first-run screen to arbitrary community packs (low-quality, spam, or unsafe cover images /
   titles). What is the gate — a minimum member count, an allowlist of relays/curators, kernel-side
   NSFW/keyword filtering on title+image, or a "report pack" affordance? Needed before enabling
   the network tier in production.
3. **How many of each kind before "see more".** Section 4.3 caps ~8 cards total. With both tiers
   live, confirm the per-kind quota (e.g. up to 5 media + 3 general visible, rest behind lazy
   scroll) and whether network packs are paginated or hard-capped.
4. **Initial pablof7z seed packs — actual contents.** What media (39092) and general (39089)
   packs should pablof7z publish at launch, and who maintains/refreshes them? Need the real list
   of packs, their member rosters, titles, cover images, and `d`-tags so featured content exists
   on day one.
5. **Resolve pablof7z's real pubkey.** The implementation must substitute the real hex/npub for
   `FEATURED_CURATOR_PUBKEY`; confirm which identity (and whether a separate publishing key is
   preferred over the personal one).

---

## Sources

- NIP-51 (List Event Kinds; Sets metadata) — https://github.com/nostr-protocol/nips/blob/master/51.md
- NIPs index — https://github.com/nostr-protocol/nips
- following.space (real follow-pack create/explore client) — https://github.com/callebtc/following.space
- Olas (pablof7z) — https://github.com/pablof7z/olas
- NDK (NIP-51 list/follow handling) — https://github.com/nostr-dev-kit/ndk
