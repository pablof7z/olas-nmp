# Wiki Index

> Derived cache — do not hand-edit. Rebuilt by proactive-context after each capture.

Last updated: 2026-06-19

## android-platform (1 guide)

| Slug | Title | Summary | Tags | Volatility | Verified | Topic |
|------|-------|---------|------|------------|----------|-------|
| [android-platform](guides/android-platform.md) | Android Platform | The Android app uses applicationId io.f7z.olas with compileSdk 34, minSdk 26, and Compose enabled | capture | warm | 2026-06-19 | android-platform |

## code-standards (2 guides)

| Slug | Title | Summary | Tags | Volatility | Verified | Topic |
|------|-------|---------|------|------------|----------|-------|
| [ai-workflow-patterns](guides/ai-workflow-patterns.md) | AI Workflow Patterns | CLAUDE.md defines two recurring multi-agent patterns: 'brainstorm' (parallel Opus agent and Codex exec independently, then synthesized into one output) and 'PER | capture | warm | 2026-06-19 | code-standards |
| [code-size-limits](guides/code-size-limits.md) | Code Size Limits | The project maintains a soft limit of 300 lines of code and a hard limit of 500 lines of code per source code file, as documented in AGENTS.md. | capture | warm | 2026-06-19 | code-standards |

## ios-platform (1 guide)

| Slug | Title | Summary | Tags | Volatility | Verified | Topic |
|------|-------|---------|------|------------|----------|-------|
| [ios-platform](guides/ios-platform.md) | iOS Platform | The iOS app bundle identifier is io.f7z.olas with team 456SHKPP26, targeting iOS 17.0 and Swift 6.0 | capture | warm | 2026-06-19 | ios-platform |

## nmp-app (3 guides)

| Slug | Title | Summary | Tags | Volatility | Verified | Topic |
|------|-------|---------|------|------------|----------|-------|
| [architecture](guides/architecture.md) | Architecture | Rust owns all business logic; Swift and Kotlin only render UI and call capabilities through the NMP bridge | capture | warm | 2026-06-19 | nmp-app |
| [cross-platform-parity](guides/cross-platform-parity.md) | Cross-Platform Parity | Olas is an NMP-based app (../nostr-multi-platform) that MUST keep iOS and Android in feature-parity at all times â every PR touching one platform must ship th | capture | warm | 2026-06-19 | nmp-app |
| [nmp-app-overview](guides/nmp-app-overview.md) | NMP App Overview | Olas is an NMP (Nostr Multi-Platform) app located at ../nostr-multi-platform | capture | warm | 2026-06-19 | nmp-app |

## product (9 guides)

| Slug | Title | Summary | Tags | Volatility | Verified | Topic |
|------|-------|---------|------|------------|----------|-------|
| [image-filters](guides/image-filters.md) | Image Filters | Image filters are available when publishing posts | capture | warm | 2026-06-19 | product |
| [media-server-config](guides/media-server-config.md) | Media Server Configuration | The default media server is blossom.primal.net, with media servers configurable from app settings | capture | warm | 2026-06-19 | product |
| [nostr-protocol](guides/nostr-protocol.md) | Nostr Protocol | The app uses NIP-68 (kind 20) for picture events with imeta tags containing url, x (SHA-256), m (MIME), dim, blurhash, and alt | capture | warm | 2026-06-19 | product |
| [onboarding](guides/onboarding.md) | Onboarding | The onboarding flow is a 6-step process: Splash â Welcome â Create account â Follow packs â Media server â All set! Account creation must not expose t | capture | warm | 2026-06-19 | product |
| [post-interactions](guides/post-interactions.md) | Post Interactions | Post reactions use deliberate silent-undo asymmetry on like/unlike animation, with a double-tap gesture and Follow state machine. | capture | warm | 2026-06-19 | product |
| [product-vision](guides/product-vision.md) | Product Vision | Olas is a private-first photo-feed-first client for small communities, pivoted away from NIP-29 group features (to be added later) toward a pictures-feed-first | capture | warm | 2026-06-19 | product |
| [progressive-disclosure](guides/progressive-disclosure.md) | Progressive Disclosure | The end consumer is non-technical, but progressive disclosure allows digging into more technical settings | capture | warm | 2026-06-19 | product |
| [ui-design-system](guides/ui-design-system.md) | UI Design System | Product planning must include specific polish specs with dedicated agents specing out the feel of the app exclusively | capture | warm | 2026-06-19 | product |
| [wot-and-feed-filtering](guides/wot-and-feed-filtering.md) | WoT and Feed Filtering | WoT filtering uses nmp-wot; if something is inadequate or incomplete, gaps must be skeptically validated by Codex before filing a GitHub issue on the NMP repo, | capture | warm | 2026-06-19 | product |

## relay (1 guide)

| Slug | Title | Summary | Tags | Volatility | Verified | Topic |
|------|-------|---------|------|------------|----------|-------|
| [relay-deployment](guides/relay-deployment.md) | Relay Deployment | The relay runs on port 12304 at 127.0.0.1 on host 157.180.102.242, deployed to /opt/olas-relay/ | capture | warm | 2026-06-19 | relay |

## Research Records (1 record)

| Record | Date | Finding | Agent |
|--------|------|---------|-------|
| [2026-06-19-1-pert-workflow-review-report-boundary-and](research/2026-06-19-1-pert-workflow-review-report-boundary-and.md) | 2026-06-19 | PERT workflow review report: boundary and parity audit of iOS/Android Olas app — verdict FAILED (boundary violations found: iOS reimplements SHA-256 hashing and kind:20 event construction in Swift instead of dispatching through Rust; critical parity break between platforms' upload pipelines) | PERT workflow agent (task wa8cgxtbi) |

## Episode Cards (30 cards)

| Card | Date | Title | Salience | Status |
|------|------|-------|----------|--------|
| [2026-06-19-1-adr-0053-projection-consumption-crash-root](episodes/2026-06-19-1-adr-0053-projection-consumption-crash-root.md) | 2026-06-19 | ADR-0053 projection-consumption crash root cause | root-cause | active |
| [2026-06-19-1-ios-android-feature-parity-enforced-as](episodes/2026-06-19-1-ios-android-feature-parity-enforced-as.md) | 2026-06-19 | iOS/Android feature parity enforced as non-negotiable invariant | architecture | superseded |
| [2026-06-19-1-ios-android-strict-feature-parity-doctrine](episodes/2026-06-19-1-ios-android-strict-feature-parity-doctrine.md) | 2026-06-19 | iOS/Android strict feature-parity doctrine | architecture | superseded |
| [2026-06-19-1-nmp-app-olas-must-be-thin](episodes/2026-06-19-1-nmp-app-olas-must-be-thin.md) | 2026-06-19 | nmp-app-olas must be thin FFI glue — no local reimplementations of NMP crates | architecture | active |
| [2026-06-19-1-nmp-boundary-nmp-app-olas-must](episodes/2026-06-19-1-nmp-boundary-nmp-app-olas-must.md) | 2026-06-19 | NMP boundary: nmp-app-olas must not reimplement NMP-owned capabilities | architecture | active |
| [2026-06-19-1-nmp-delegation-replaces-local-reimplementation](episodes/2026-06-19-1-nmp-delegation-replaces-local-reimplementation.md) | 2026-06-19 | NMP delegation replaces local reimplementation | architecture | superseded |
| [2026-06-19-1-olas-layout-restructured-from-root-level](episodes/2026-06-19-1-olas-layout-restructured-from-root-level.md) | 2026-06-19 | Olas layout restructured from root-level dirs to apps/ monorepo pattern | reversal | superseded |
| [2026-06-19-1-olas-pivoted-from-nip-29-group](episodes/2026-06-19-1-olas-pivoted-from-nip-29-group.md) | 2026-06-19 | Olas pivoted from NIP-29 group app to photo-feed-first client | reversal | active |
| [2026-06-19-1-olas-pivots-from-nip-29-communities](episodes/2026-06-19-1-olas-pivots-from-nip-29-communities.md) | 2026-06-19 | Olas pivots from NIP-29 communities to photo-feed-first Nostr client | reversal | superseded |
| [2026-06-19-1-pivot-from-nip-29-communities-to](episodes/2026-06-19-1-pivot-from-nip-29-communities-to.md) | 2026-06-19 | Pivot from NIP-29 communities to photo-feed-first client | reversal | superseded |
| [2026-06-19-1-pivot-from-nip-29-private-communities](episodes/2026-06-19-1-pivot-from-nip-29-private-communities.md) | 2026-06-19 | Pivot from NIP-29 private communities to photo-feed-first Nostr client | reversal | superseded |
| [2026-06-19-2-adr-0053-nmp-app-consume-all](episodes/2026-06-19-2-adr-0053-nmp-app-consume-all.md) | 2026-06-19 | ADR-0053: nmp_app_consume_all_builtin_projections required before nmp_app_start | root-cause | superseded |
| [2026-06-19-2-exif-stripping-and-geohash-only-location](episodes/2026-06-19-2-exif-stripping-and-geohash-only-location.md) | 2026-06-19 | EXIF stripping and geohash-only location policy for photo posts | product | active |
| [2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas](episodes/2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas.md) | 2026-06-19 | NMP architecture doctrine: nmp-app-olas is a thin delegation crate, not a reimplementation | architecture | superseded |
| [2026-06-19-2-nmp-boundary-protection-no-hacking-around](episodes/2026-06-19-2-nmp-boundary-protection-no-hacking-around.md) | 2026-06-19 | NMP boundary protection: no hacking around gaps | architecture | active |
| [2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream](episodes/2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream.md) | 2026-06-19 | NMP gap-handling doctrine: validate upstream, never hack around | architecture | superseded |
| [2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must](episodes/2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must.md) | 2026-06-19 | NMP WoT boundary doctrine: gaps must be fixed upstream, not hacked around | architecture | superseded |
| [2026-06-19-2-olas-governance-doctrines-mandatory-ios-android](episodes/2026-06-19-2-olas-governance-doctrines-mandatory-ios-android.md) | 2026-06-19 | Olas governance doctrines: mandatory iOS/Android parity and 300/500 LOC limits | architecture | active |
| [2026-06-19-2-onboarding-uses-nip-51-follow-packs](episodes/2026-06-19-2-onboarding-uses-nip-51-follow-packs.md) | 2026-06-19 | Onboarding uses NIP-51 follow packs with hidden key jargon | product | active |
| [2026-06-19-2-progressive-disclosure-nostr-protocol-vocabulary-hidden](episodes/2026-06-19-2-progressive-disclosure-nostr-protocol-vocabulary-hidden.md) | 2026-06-19 | Progressive disclosure — Nostr protocol vocabulary hidden from users | product | active |
| [2026-06-19-2-project-layout-restructured-to-apps-monorepo](episodes/2026-06-19-2-project-layout-restructured-to-apps-monorepo.md) | 2026-06-19 | Project layout restructured to apps/ monorepo convention | reversal | active |
| [2026-06-19-3-background-upload-miniplayer-replaces-blocking-compose](episodes/2026-06-19-3-background-upload-miniplayer-replaces-blocking-compose.md) | 2026-06-19 | Background upload miniplayer replaces blocking compose sheet | product | active |
| [2026-06-19-3-ios-android-strict-feature-parity-enforced](episodes/2026-06-19-3-ios-android-strict-feature-parity-enforced.md) | 2026-06-19 | iOS/Android strict feature parity enforced via Rust-owns-logic architecture | architecture | active |
| [2026-06-19-3-seamless-account-creation-hides-nostr-key](episodes/2026-06-19-3-seamless-account-creation-hides-nostr-key.md) | 2026-06-19 | Seamless account creation hides Nostr key jargon behind username@olas.app | product | superseded |
| [2026-06-19-3-wot-gap-handling-doctrine-no-local](episodes/2026-06-19-3-wot-gap-handling-doctrine-no-local.md) | 2026-06-19 | WoT gap handling doctrine — no local hacks, fix upstream | architecture | superseded |
| [2026-06-19-3-wot-scoring-unavailable-network-feed-shown](episodes/2026-06-19-3-wot-scoring-unavailable-network-feed-shown.md) | 2026-06-19 | WoT scoring unavailable: network feed shown unfiltered until upstream gap resolves | product | active |
| [2026-06-19-3-zero-stubs-doctrine-real-relays-real](episodes/2026-06-19-3-zero-stubs-doctrine-real-relays-real.md) | 2026-06-19 | Zero-stubs doctrine: real relays, real keys, real filters | architecture | active |
| [2026-06-19-4-native-tabview-replaces-custom-olastabbar-for](episodes/2026-06-19-4-native-tabview-replaces-custom-olastabbar-for.md) | 2026-06-19 | Native TabView replaces custom OlasTabBar for liquid glass | reversal | active |
| [2026-06-19-4-seamless-account-creation-username-olas-app](episodes/2026-06-19-4-seamless-account-creation-username-olas-app.md) | 2026-06-19 | Seamless account creation — username@olas.app, no key jargon | product | superseded |
| [2026-06-19-5-ios-upload-pipeline-unified-to-canonical](episodes/2026-06-19-5-ios-upload-pipeline-unified-to-canonical.md) | 2026-06-19 | iOS upload pipeline unified to canonical Rust dispatch path | architecture | active |

