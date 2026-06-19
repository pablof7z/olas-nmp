# Olas — User Flows

Design north star: a non-technical person should install Olas, post their first photo, and scroll a real feed without ever seeing "Nostr," "relay," "pubkey," or "web of trust." Every protocol concept has a human-facing name and a sensible default.

**Cross-flow conventions:**
- Bottom tabs: Home · Search · ⊕ · Notifications · Profile
- "Recovery Key" = nsec. Never called nsec in user-facing text.
- "Account address" = npub. Used only in share/copy contexts.
- Sheets are native bottom sheets. Navigation is iOS push/pop, Android shared-axis.

---

## Flow 1 — New user onboarding

**Goal:** Cold install → scrolling a populated feed. Target: under 90 seconds, zero protocol vocabulary.

1. Full-bleed welcome screen. Looping muted photo mosaic in background. Single line: *"Your photos. Your network. No algorithm."* Two buttons: **Get started** (primary) and **I have an account** (secondary text link).
2. Tap **Get started** → **Name your profile**: display name field + avatar circle (tap to pick photo from library or camera). Optional, but prompted first because it's familiar.
3. In the background during steps 2–5: keypair generated silently. No mention.
4. **Secure your account** screen. Framed: *"If you ever get a new phone, this is how you get back in."* Three-line explainer (no jargon). Primary action: **Save to Keychain** (iCloud Keychain on iOS, Google Password Manager on Android). Secondary: **Copy key** (shows the nsec). **Skip for now** tertiary link — shows a small "!" badge on Profile tab, re-prompts 48h later. Cannot proceed without tapping one of these three.
5. **Where to post** screen. A single pre-selected option ("Olas Network — fast and free"). A collapsed "Choose your own" disclosure for power users. Most users tap **Continue** without expanding.
6. **Follow some people** screen. A grid of suggested accounts with a sample photo strip per card, categorized by chips (Photography / Travel / Food / Art / Friends). **Follow** toggle per card. Live counter at top: *"Following 6 — follow a few more for a better feed."* Minimum is 0; **Done** is always available.
7. Feed loads immediately if they followed anyone. A one-time coachmark points at ⊕: *"Ready to share? Tap to post."*

**Friction point:** Step 4 (Recovery Key). Fix: default to OS-keychain (one tap, key never seen). Frame it as backup-recovery ("like forgetting your password"), not cryptographic identity.

---

## Flow 2 — Existing user sign-in

1. Tap **I have an account** from welcome screen.
2. Sign-in chooser sheet. Three equally-weighted cards:
   - **Paste recovery key** — "Have a code starting with nsec1? Paste it here."
   - **Use a signing app** (Android: Amber auto-detected and shown as "Continue with Amber" above the fold if installed)
   - **Connect remotely** — bunker URL or QR scan for NIP-46
3. **Path A — nsec paste:** Single field with **Paste** button. Validates prefix and checksum live. On valid nsec: **Save to Keychain** prompt (default on). Invalid: inline "That doesn't look right — a recovery key starts with nsec1."
4. **Path B — NIP-55 signer:** Fires OS intent to installed signer. If none found: "You'll need a signing app" with store link. Returns signed-in after signer approves.
5. **Path C — NIP-46 bunker:** Paste bunker URL or scan QR. "Connecting…" state while handshaking. Signer app shows approval prompt on other device.
6. After any path: profile + follow graph hydrates. Feed appears with skeleton loading → real content. Never empty (falls back to starter packs if follows list is empty).

**Friction point:** Step 2 — three unfamiliar options. Fix: on Android, auto-detect Amber and surface "Continue with Amber" first. Add "Not sure which?" explainer link with three sentences.

---

## Flow 3 — Posting a photo

1. Tap **⊕** in tab bar.
2. Native photo picker opens to camera roll, most-recent first. Camera shortcut in corner. Multi-select indicator shows "Tap to select up to 10."
3. Select image(s). Tap **Next**.
4. **Edit screen.** Full-width preview with filter strip below (horizontal scroll, live-preview thumbnails). Tap a filter → applies with crossfade. Intensity slider appears for selected filter. **Adjust** tab alongside for brightness/contrast/etc. For carousels: thumbnail row below edit area; **Apply to all** button.
5. Tap **Next**.
6. **Compose screen:**
   - Caption field (autofocus, keyboard up). #hashtag and @mention autocomplete inline.
   - Collapsed **Alt text** chip per image ("Add description for accessibility").
   - **Location** toggle (off by default; shows privacy note on enable).
   - **Posting to** row: "Olas Network · blossom.primal.net" — tappable to change (see Flow 10).
   - **Advanced** disclosure: relay selector, Blossom server override for this post.
7. Tap **Share** (top-right).
8. Post appears immediately in the Following feed with a pulsing upload indicator. In background: hash → upload → build event → sign → publish to relays.
9. Upload indicator resolves to a checkmark. Toast: *"Posted."* If relay confirmation takes >5s, the post persists with a "Syncing…" label — never a spinner blocking the user.

**Friction point:** Step 8 — slow uploads on cellular. Fix: compress before upload, show real % progress (not indeterminate), resume chunked uploads on reconnect, publish-on-background with system notification.

---

## Flow 4 — Posting a video

Same as Flow 3 with these additions:

- After selection, **Trim screen** first: filmstrip with drag handles, cover-frame scrubber.
- Compress step runs client-side before upload; progress shown as "Processing… / Uploading 43%."
- Cover frame previews in compose screen.
- Warning before large upload on cellular: "This video is ~38 MB. Upload on Wi-Fi?" with "Upload anyway" option.
- Background upload with a system notification when complete.

---

## Flow 5 — Interacting with a post

**♥ Like (react):** Tap → fills red, +1 optimistic. Double-tap on image → heart burst animation (see motion spec). Tap again → unlike (silent, no haptic).

**💬 Comment:** Tap → post expands to thread view with reply field focused. Comments render oldest-first, one level of reply threading. Posting is optimistic.

**⚡ Zap:**
- First ever zap → wallet setup sheet (see Flow 5a below).
- Subsequent: tap → sends default amount (e.g., 21 sats) instantly with coin animation.
- Long-press ⚡ → amount picker: preset chips (21 / 100 / 500 / 1k / custom) + optional comment.

**↗ Share:** Native share sheet. Options: Copy link (njump URL) / Share to… / Copy account address (npub — in overflow).

**⋯ Overflow:** Save to camera roll / Mute account / Report / Copy link / Why am I seeing this?

**Flow 5a — First zap (wallet setup):**
1. Sheet appears: *"Zaps are real tips in Bitcoin. Here's how it works:"* — three-line explainer.
2. Single recommended option: "Connect with a wallet app" → QR code or deep link for NWC.
3. If a compatible wallet is already installed (detected via URL scheme), show "Continue with [Wallet Name]" as primary CTA.
4. Default amount picker (21 / 100 / 500 sats). **"Set this up later"** is always available.
5. After connecting: shows "Wallet connected — you're ready to zap" confirmation, then immediately completes the zap that triggered the setup.

---

## Flow 6 — Discovering users

**Path A — Suggested (Search tab default):** Shows 2nd-degree WoT accounts ranked by mutual-follow count. Each card: avatar, name, sample photo strip, "Followed by Ana + 3 others." Follow toggles inline.

**Path B — Magazine browse:** Search tab "Browse" section lists curated per-relay feeds. Tapping opens relay magazine view. Any post → profile → follow.

**Path C — Search:** Text field → results tabbed: People / Photos / Tags. People ranked by trust proximity; verified/NIP-05 accounts get a subtle check badge. No raw npub or pubkey shown in results.

**Friction point:** Disambiguation — many similar-display-name accounts. Fix: float trust-connected accounts ("Followed by people you follow") to top; demote zero-connection strangers below a fold.

---

## Flow 7 — Profile view

**Own profile:**
- Reached via Profile tab.
- Header: avatar, name, bio, stats, **Edit profile** button, ⚙ gear (→ Settings).
- If Recovery Key was skipped: dismissible "Back up your account" banner at top.
- Grid of own posts; tap any → full post view with edit/delete in ⋯.

**Other profile:**
- Reached from feed, search, notification, or comment.
- Header: avatar, name, bio, WoT line ("Followed by Ana + 4 others"), **Follow/Following** button, **Zap** button, **⋯** (Mute / Report / Copy link).
- Grid of their posts; tap → full post view with standard engagement actions.

**Profile edit propagation:** On Save → publishes replaceable kind-0 → UI confirms only after ≥1 relay accepts → auto-re-broadcasts to all write relays. Never shows "Saved" before relay confirmation.

---

## Flow 8 — Settings (progressive disclosure)

Three tiers. See [`features.md`](features.md) §Settings for the full list.

**Key interaction rule:** Advanced section is **sticky** — once a user opens it, it stays open (self-identified power user). Every Tier-3 screen has **"Reset to recommended"** in one tap. No state in Tier 3 can permanently break the app.

---

## Flow 9 — Adjusting the WoT filter

Entry: Settings → Content & filtering, or filter icon at the top of the Home feed.

1. Label: *"Who shows up in your feed."* Three preset cards:
   - **Close** — "Just the people you follow and those they follow closely."
   - **Balanced** *(default)* — "Your broader network — friends of friends."
   - **Open** — "Everyone. More to discover, less filtered."
2. Selecting a preset updates a live **mini-feed preview** below the cards — 3–4 sample posts. The effect is felt, not read.
3. Choosing **Open** triggers a one-time: *"You'll start seeing people outside your network. You can switch back anytime."* — dismissable, not blocking.
4. Advanced disclosure: raw min-score slider + toggles (hide accounts with no connections / hide accounts created < 30 days ago).
5. The Home feed's filter icon shows the current preset name as a label. Two taps to change from anywhere.

---

## Flow 10 — Adding / changing a media server

Entry: Settings → Advanced → Servers, or **Posting to** row in composer.

1. Servers screen: primary server as a named card (e.g., "blossom.primal.net — Connected ●"), health dot, latency. Additional servers below.
2. Tap **⊕ Add server**: curated list with name + region + trust note, or **"Add by URL"** field.
3. On add: Olas runs a connection test (auth handshake + tiny test blob upload). Pass/fail shown before saving.
4. Drag to reorder (sets primary). **Mirror to all** toggle copies new uploads to all servers.
5. On change: explicit note — *"New posts will use this server. Existing posts stay on their original server."*
6. Remove: swipe → Remove. Blocked if last server (must add another first).
7. From composer: a lightweight bottom sheet showing saved servers, no management UI — fast, in-context picker only.
