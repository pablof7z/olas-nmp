# Olas UX Improvements — P0–P3 Backlog & PERT Execution Plan

Source: synthesis of 5 parallel Opus UX-brainstorm agents (onboarding, feed, compose, social/discovery, polish), each grounded in the real codebase with file:line citations. This document is the authoritative task list for the PERT execution effort.

Legend: **Effort** S/M/L · **Impact** low/med/high · **Plat** = platforms touched (iOS / Android / Rust).

---

## P0 — "Make it honest" (broken/faked features the UI already promises)

These are bugs masquerading as features. Highest impact-to-effort in the codebase.

| ID | Item | Plat | Key files | Notes |
|----|------|------|-----------|-------|
| P0-A | Onboarding follows real people | iOS/Android/Rust | `Onboarding/FollowPacksView.swift`, `Core/Models.swift` (`FollowPack.defaults`, `previewPubkeys: []`), Rust pack discovery/apply | Selecting packs currently follows **zero** people → empty feed. ⚠️ overlaps background media-server agent + onboarding-flow doc. |
| P0-B | Multi-photo posts actually publish | iOS/Android/Rust | `Core/UploadQueue.swift`, `Compose/*`, `nmp-app-olas/src/actions.rs` (`olas_picture_post_publish_json`) | UI collects `[UIImage]` but only `images[0]` uploads; Android `uris.first()`. Emit multiple NIP-68 imeta tags. ⚠️ overlaps bg agent. |
| P0-C | Real byte-level upload progress + retry | iOS/Android/Rust | `Core/UploadQueue.swift`, `UploadMiniPlayer`, Rust `nmp.blossom.upload` progress events | Progress bar is faked (frozen 10% → jump). ⚠️ overlaps bg agent. |
| P0-D | Wire up filter intensity slider | iOS/Android | `Compose/FilterCarouselView.swift`, `PhotoFilters.apply(_:to:intensity:)`, Android `FilterCarousel.kt` | Slider exists; `intensity` arg ignored (always 100%). Native-only. |
| P0-E | Real social proof (kill hardcoded string) | iOS/Android/Rust | `Profile/ProfileHeaderView.swift:~128`, `Search/SuggestedAccountCard.swift`, `Search/SearchView.swift`, Rust `social_proof()` FFI | `"Followed by people you follow"` is hardcoded for everyone. |
| P0-F | Real Discover rows (not cache dump) | iOS/Android/Rust | `Search/DiscoverView.swift`, Rust `discover_sections()` FFI | Currently `profileCache.values.prefix(20)`, unranked. |
| P0-G | Dead-UI sweep | iOS/Android | notif filter `Button {}` (`Notifications/NotificationsView.swift:~135`), Android geohash `= null` in publish, render discarded `blurhash`/`alt`/`dimensions` | Several small fixes; geohash is a parity bug. (Blurhash render = GitHub #38, see P1-B.) |

## P1 — Feel (close the "functional → premium" gap)

| ID | Item | Plat | Key files | Notes |
|----|------|------|-----------|-------|
| P1-A | Haptics + sound layer | iOS/Android | new `OlasHaptics` in `Theme.swift`, wire into like/zap/filter/mode-switch/new-posts-pill; ShutterSoft + ZapChime (opt-in) in `Settings` | Zero `UIImpactFeedback`/`AVAudioPlayer` today. Native-only. Highest feel/LOC. |
| P1-B | Blurhash placeholders + shimmer skeletons | iOS/Android (Rust optional) | `Components/ImageLoading/CachedImage.swift`, `Core/Models.swift` (`ImageMeta.blurhash`), `FeedSkeletonView` | GitHub issue **#38**. Data already decoded & discarded. |
| P1-C | Photo-lift matched-geometry zoom + reserved layout | iOS/Android | `Feed/FeedView.swift`, `Feed/FullscreenImageView.swift`, `Profile/ProfileGridView.swift` | Generic modal slide today; reserve box from `dimensions` to kill layout shift. |

## P2 — Growth (the missing compounding loop)

| ID | Item | Plat | Key files | Notes |
|----|------|------|-----------|-------|
| P2-A | Inbound invite links (warm-start) | iOS/Android/Rust | Universal Links / App Links, deferred deep link, Rust invite-token → inviter pubkey + ad-hoc pack | No invite mechanic exists at all. |
| P2-B | First-post aha | iOS/Android | `Onboarding/OnboardingCompleteView.swift` → route into compose; first-post coachmark | Ends on "go scroll"; it's a photo-*sharing* app. |
| P2-C | Outbound "bring a friend" | iOS/Android/Rust | share sheet + Rust mint invite token | Compounds P2-A. |

## P3 — Depth (bigger bets)

| ID | Item | Plat | Key files | Notes |
|----|------|------|-----------|-------|
| P3-A | Camera-first capture | iOS/Android | new capture step (AVCaptureSession / CameraX) → existing edit flow | No in-app camera in compose. |
| P3-B | Grouped + sectioned notifications | iOS/Android/Rust | `Notifications/NotificationsView.swift`, `NotificationItemView.swift` (`groupCount` exists, unused), Rust pre-grouped clusters | 12 likes = 12 rows today. |
| P3-C | @mentions + #hashtags in caption | iOS/Android/Rust | `Compose/CaptionView.swift`, Rust `parse_caption_tags` (NIP-27 p/t tags) | Discovery/threading depend on tags. |
| P3-D | Account recovery safety net | iOS/Android/Rust | onboarding optional passkey/email backup | ⚠️ architectural; silent permanent account-loss risk. Needs product decision. |

---

## Execution constraints (PERT)

1. **Base commit required first.** Working tree has large uncommitted WIP incl. untracked `Components/ImageLoading/`. Commit a clean base on a feature branch before any worktree-isolated execute agent (else worktrees from HEAD lose the WIP).
2. **Background media-server agent** is live in the main tree on onboarding/`UploadQueue`/`actions.rs`. Do not touch P0-A/B/C until it lands.
3. **Shared Rust crate** (`nmp-app-olas`): FFI-adding slices (P0-E, P0-F, P2, P3-B, P3-C) must serialize their Rust edits or batch into one Rust pass to avoid conflicts + redundant `rust-ios-sim`/`rust-android` rebuilds.
4. **Single iOS sim + single Android emulator** → the Haiku **Test** phase must be serialized (one feature on-device at a time).
5. **Native-only slices** (P0-D, P0-G, P1-A, P1-B, P1-C, P2-B, P3-A) can run execute in parallel worktrees safely — no Rust rebuild contention.

## PERT wave ordering

- **Wave 1 (native-only, parallel):** P1-B (blurhash #38), P1-A (haptics), P0-G (dead-UI), P0-D (filter intensity), P2-B (first-post aha).
- **Wave 2 (Rust batch #1, after bg agent + base commit):** P0-A, P0-B, P0-C (onboarding/upload/Rust).
- **Wave 3 (Rust batch #2):** P0-E, P0-F (social proof + discover FFI).
- **Wave 4:** P1-C (photo-lift), P3-A (camera), P3-B (notifications), P3-C (mentions).
- **Wave 5:** P2-A/P2-C (invites), P3-D (recovery — gated on product decision).

Each item runs a PERT micro-cycle: **Plan** (codex) → **Execute** (Sonnet, worktree) → **Review** (Opus vs plan + AGENTS.md) → **Test** (Haiku on sim/emulator, screenshots). Review-fail → back to Execute; Test-fail → back to Execute (not Plan).
