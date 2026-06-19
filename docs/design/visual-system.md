# Olas — Visual Design System

## Philosophy

The UI exists to deliver photos to eyes as fast and cleanly as possible, then disappear. Chrome is near-invisible. Color lives in the content. Every non-photo pixel earns its presence or it's removed.

Three rules govern every visual decision:
1. **Photos are the only color.** UI chrome is black, white, and gray — never a competing hue.
2. **Restraint over personality.** No gradients, drop shadows, or decorative texture except where they serve direct functional clarity.
3. **Platform-native, not cross-platform.** iOS uses SF Pro and Apple's spatial conventions. Android uses Google Sans and Material spacing. The experience feels native to each platform, not ported.

---

## Color System

All colors defined as design tokens. Suffix `:light` / `:dark` where they differ.

### Light Mode

| Token | Hex | Usage |
|---|---|---|
| `background` | `#FFFFFF` | Screen background, nav bar |
| `surface` | `#F5F5F5` | Cards, input fields, secondary surfaces |
| `surface-elevated` | `#FFFFFF` | Sheets, modals (separated by shadow, not color) |
| `border` | `#E8E8E8` | Dividers, input outlines, card borders |
| `border-subtle` | `#F0F0F0` | Between-card separators |
| `text-primary` | `#0A0A0A` | Headlines, usernames, body |
| `text-secondary` | `#6B6B6B` | Timestamps, metadata, placeholder |
| `text-tertiary` | `#ADADAD` | Disabled states, hints |
| `interactive` | `#0A0A0A` | Buttons, links, active icons (near-black) |
| `interactive-pressed` | `#2A2A2A` | |
| `destructive` | `#E5332A` | Delete, report, error actions |
| `success` | `#1A8A45` | Upload confirmed, post sent |
| `zap` | `#F59E0B` | Lightning / zap icon, zap count |
| `overlay-light` | `rgba(255,255,255,0.72)` | Frosted content overlays |
| `overlay-dark` | `rgba(0,0,0,0.48)` | Image caption overlays |
| `skeleton-base` | `#E8E8E8` | Loading placeholder fill |
| `skeleton-highlight` | `#F5F5F5` | Shimmer sweep |

### Dark Mode

| Token | Hex | Usage |
|---|---|---|
| `background` | `#0A0A0A` | Screen background |
| `surface` | `#161616` | Cards, inputs |
| `surface-elevated` | `#1E1E1E` | Sheets (no shadow needed, uses border instead) |
| `border` | `#2E2E2E` | Dividers |
| `border-subtle` | `#1A1A1A` | Between-card separators |
| `text-primary` | `#F5F5F5` | Headlines, usernames |
| `text-secondary` | `#999999` | Timestamps, metadata |
| `text-tertiary` | `#555555` | Disabled, hints |
| `interactive` | `#F5F5F5` | Buttons, links (near-white) |
| `interactive-pressed` | `#CCCCCC` | |
| `destructive` | `#FF5B54` | |
| `success` | `#34C759` | (iOS system green) |
| `zap` | `#FBB131` | |
| `overlay-light` | `rgba(30,30,30,0.72)` | |
| `overlay-dark` | `rgba(0,0,0,0.64)` | |
| `skeleton-base` | `#242424` | |
| `skeleton-highlight` | `#2E2E2E` | |

### Photo-forward rule
When a photo is full-screen or in immersive media view, all chrome (`background`, `surface`, navigation elements) transitions to `#000000` pure black. The photo is the entire world. No UI element competes with the image.

### Tint / accent policy
**There is no brand accent color.** The interactive color (`#0A0A0A` light / `#F5F5F5` dark) is near-black/near-white — a photographic choice. The only exception: the zap/lightning color (`#F59E0B`) is the single warm hue in the system, reserved exclusively for zap affordances. No blue. No brand orange. No gradient.

---

## Typography

### Platform families
- **iOS:** SF Pro Display (titles ≥ 20pt) + SF Pro Text (body, UI, captions). Respect Dynamic Type.
- **Android:** Google Sans Display (titles ≥ 20sp) + Google Sans (body/UI) + Roboto (fallback). Respect font scale.

### Type scale

| Style | iOS | Android | Size | Weight | Line-height | Tracking |
|---|---|---|---|---|---|---|
| Large Title | SF Pro Display | Google Sans Display | 34pt/sp | Regular | 41pt | 0 |
| Title 1 | SF Pro Display | Google Sans Display | 28pt/sp | Semibold | 34pt | −0.3 |
| Title 2 | SF Pro Display | Google Sans Display | 22pt/sp | Semibold | 28pt | −0.2 |
| Headline | SF Pro Text | Google Sans | 17pt/sp | Semibold | 22pt | −0.4 |
| Body | SF Pro Text | Google Sans | 17pt/sp | Regular | 22pt | −0.4 |
| Callout | SF Pro Text | Google Sans | 16pt/sp | Regular | 21pt | −0.3 |
| Subheadline | SF Pro Text | Google Sans | 15pt/sp | Regular | 20pt | −0.2 |
| Caption | SF Pro Text | Google Sans | 13pt/sp | Regular | 18pt | 0 |
| Caption Small | SF Pro Text | Google Sans | 11pt/sp | Regular | 13pt | +0.1 |
| Footnote | SF Pro Text | Google Sans | 13pt/sp | Regular | 18pt | −0.1 |

### Feed-specific type rules

| Element | Style | Notes |
|---|---|---|
| Username in feed | 14pt Semibold / −0.3 tracking | Tight, editorial |
| Caption in feed | 14pt Regular / 19pt line-height | Comfortable reading, full color |
| Comment text | 14pt Regular | Same as caption |
| Reply username | 14pt Semibold | Same weight as inline username |
| Timestamp / metadata | 13pt / `text-secondary` | Always lowercase ("2h", "just now") |
| Reaction count | 13pt Semibold / tabular figures | "47 reactions" not "47 ❤️" |
| WoT trust line | 13pt Regular / `text-secondary` | "Followed by Ana + 3 others" |
| Relay name in magazine | 11pt Semibold / all-caps / +1.2 tracking | Editorial header treatment |
| Zap amount | 13pt Semibold / `zap` color / tabular figures | "⚡ 1,240 sats" |
| Error messages | 15pt Regular / `destructive` | Sentence case, never all-caps |
| Settings labels | 17pt Regular / `text-primary` | System default |
| Advanced settings | 15pt Regular / `text-secondary` | Visually de-emphasized |

### Typographic voice rules
1. Timestamps are always lowercase, human-relative: "2h", "just now", "yesterday", "Jun 12". Never "2 hours ago" (too verbose for a feed).
2. Empty state headlines use sentence case, warm tone: "Nothing here yet" not "No Content Found."
3. Error messages address the user as a helper would: "Couldn't upload this photo — tap to try again." Never "Error: Upload failed (HTTP 503)."
4. WoT/trust language never uses "Nostr," "relay," "WoT," or "pubkey." Always: "people you follow," "your network," "outside your network."

---

## Spacing & Layout Grid

Base unit: **4pt / 4dp**.

Standard values: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64

### Feed layout

| Element | Value |
|---|---|
| Image horizontal margin | 0 (full-bleed, edge to edge) |
| Action row horizontal padding | 12pt |
| Action row height | 44pt (full touch target) |
| Action icon size | 24pt |
| Gap between action icons | 16pt |
| Caption horizontal padding | 12pt |
| Caption top margin (below action row) | 4pt |
| Space below caption before comments | 4pt |
| Between-card gap | 0 (1px border-subtle separator) |

### Avatar sizes

| Context | Size | Border |
|---|---|---|
| Feed card (above image) | 32×32pt | None |
| Profile header | 80×80pt | 3pt white/dark ring |
| Comment / notification | 36×36pt | None |
| Search result | 44×44pt | None |
| Suggested account card | 56×56pt | None |
| Story-ring equivalent (future) | 64×64pt | 3pt accent ring |

### Corner radii

| Element | Radius |
|---|---|
| Feed images | 0pt (full bleed) |
| Non-feed image cards | 12pt |
| Avatar | 50% (circle) |
| Bottom sheets | 20pt (top corners) |
| Primary buttons | 12pt |
| Secondary / ghost buttons | 12pt |
| Tag / chip / pill | 100pt (capsule) |
| WoT badge | 6pt |
| Notification card | 12pt |
| Settings cell | 0pt (list style) |
| Magazine tiles | 8pt |

---

## Feed Card Anatomy

A complete picture-post card, top-to-bottom:

```
┌─────────────────────────────────────────────┐
│  ● Username          Followed by Ana  ⋯     │  ← 44pt row, 12pt H-pad
│                       [trust line]           │    Avatar 32pt, Username Headline
├─────────────────────────────────────────────┤
│                                             │
│           [ FULL-BLEED IMAGE ]              │  ← Aspect: native up to 4:5 max
│            (swipeable for carousel)         │    portrait. Landscape capped at 3:2.
│                   ● ●                       │  ← Carousel dots if >1 image
│                                             │
├─────────────────────────────────────────────┤
│  ♥  💬  ⚡  ↗                          ▼  │  ← Action row, 44pt, icons 24pt
├─────────────────────────────────────────────┤
│  47 reactions                               │  ← 12pt H-pad, Caption 13pt Semibold
│  **username** Caption text continues inline │  ← Username bold, caption regular 14pt
│  and wraps here naturally.                  │  ← 2 lines max, "more" for longer
│  View 12 comments                          │  ← Caption style, text-secondary
│  2h                                        │  ← 12pt, text-tertiary
└─────────────────────────────────────────────┘
```

**Image aspect ratio rules:**
- Portrait (taller than wide): display at native ratio up to maximum 4:5.
- Square: display at 1:1.
- Landscape (wider than tall): cap at 3:2, letterbox above/below not cropped.
- User's selection in crop tool overrides these defaults.
- Carousel: all images use the aspect ratio of the first image in the set.

**Action row icon order:**
Left to right: ♥ (react), 💬 (comment), ⚡ (zap), ↗ (share), then flex space, ▼ (save/bookmark).
Gap between left icons: 16pt. Icon size: 24pt with 44pt touch target.

---

## Button Design

### Primary button
- Fill: `interactive`
- Text: `text-primary` inverse (white in light, black in dark)
- Height: 50pt
- Corner radius: 12pt
- Horizontal padding: 24pt
- Typography: 17pt Semibold
- Pressed state: scale 0.97, opacity 0.85, 80ms

### Secondary button
- Fill: transparent
- Border: 1.5pt `border`
- Text: `interactive`
- Same sizing as primary

### Ghost / text button
- Fill: transparent
- No border
- Text: `interactive` or `text-secondary`
- Min touch target: 44×44pt
- Pressed: opacity 0.6

### Destructive button
- Fill: `destructive`
- Text: white
- Same sizing as primary
- Always accompanied by a confirmation step

### Pressed state (all types)
Spring: response 0.25, dampingFraction 0.72. Scale 0.97. Not just opacity.

---

## Iconography

### iOS
SF Symbols 5+. Weight: `medium` for all UI icons. Render at 24pt with 44pt touch target. Tab bar: `medium` inactive, `bold` active (system behavior). Accessibility: all icons paired with an `.accessibilityLabel`.

### Android
Material Symbols (outlined variant), weight 300, grade 0, optical size 24. Activated state: filled variant (weight 700). Tab bar: follows Material 3 active/inactive convention.

### Custom icons
None in v1. Only platform system icon libraries. This ensures every icon is:
- Automatically accessible
- Dynamic Type / font-scale aware
- Available in system contexts (notifications, widgets later)

### Icon to use for each action
| Action | iOS (SF Symbol) | Android (Material) |
|---|---|---|
| Like / react | `heart` / `heart.fill` | `favorite_border` / `favorite` |
| Comment | `bubble.right` | `chat_bubble_outline` |
| Zap | `bolt` / `bolt.fill` | `bolt` / `bolt` (filled) |
| Share | `arrow.up` | `ios_share` (iOS-familiar, or `share`) |
| Save / bookmark | `bookmark` / `bookmark.fill` | `bookmark_border` / `bookmark` |
| Overflow / more | `ellipsis` | `more_horiz` |
| Follow | `person.badge.plus` | `person_add` |
| Settings | `gearshape` | `settings` |
| Compose / post | `plus` | `add` |
| Search | `magnifyingglass` | `search` |
| Home / feed | `house` / `house.fill` | `home` / `home_filled` |
| Profile | `person.circle` / `.fill` | `account_circle` |
| Relay | `antenna.radiowaves.left.and.right` | `cell_tower` |
| WoT / trust | `shield.lefthalf.filled` | `verified_user` |
| Blossom / media server | `server.rack` | `dns` |
| Zap amount / wallet | `wallet.pass` | `account_balance_wallet` |

---

## Shadows & Elevation

Light mode only (dark mode uses border instead):
- **Level 1** (elevated cards, modals): `0 2pt 8pt rgba(0,0,0,0.08)`
- **Level 2** (action sheets, popovers): `0 4pt 24pt rgba(0,0,0,0.12)`

Dark mode: elevation conveyed by `border` (`#2E2E2E`) and background lightening from `#0A0A0A` → `#161616` → `#1E1E1E`. No shadows.

---

## Status & Badges

### WoT trust badge
Appears on profile header (others only) and on post-card avatar for Network feed posts.
- Shape: capsule pill
- Light: `#F5F5F5` fill, `#E0E0E0` border, `text-secondary` text
- Dark: `#242424` fill, `#2E2E2E` border
- Icon: `shield.lefthalf.filled` (SF Symbols) at 11pt, followed by "Followed by N"
- Size: 13pt / 26pt height / 10pt H-pad

### NIP-05 verified badge
- A subtle single checkmark (not double — no verification of *what* is verified)
- Placed after the display name in profile header
- `text-secondary` color, 13pt

### Upload / sync indicator
On a post card while uploading: a thin progress arc around the avatar. Completes → quick checkmark, fades out. No full-screen loading state for posting.
