---
title: Product Vision
slug: product-vision
topic: product
summary: Olas is a private-first photo-feed-first client for small communities, pivoted away from NIP-29 group features tracked in pablof7z/olas#61 toward a pictures-feed-first
tags:
  - capture
volatility: warm
confidence: medium
created: 2026-06-19
updated: 2026-06-19
verified: 2026-06-19
compiled-from: conversation
sources:
  - session:2aff77b8-e8ba-493a-b944-1fea0ecd124d
---

# Product Vision

## Product Vision

Olas is a private-first photo-feed-first client for small communities, pivoted away from NIP-29 group features tracked in pablof7z/olas#61 toward a pictures-feed-first experience. The UX must be highly polished, especially during the invite/onboarding flow and throughout the product. The app includes rich settings and per-relay views in magazine style.

<!-- citations: [^2aff7-39] [^2aff7-12] [^2aff7-24] [^2aff7-47] [^2aff7-95] -->
## Architecture & Protocols

Olas uses NIP-29 for group-based access control when the deferred scope in pablof7z/olas#61 is selected for implementation. Media supports images and videos using the proper NIPs for each type. Comments use NIP-22 threaded comments. The default Blossom media server is blossom.primal.net, but media servers are configurable from app settings. EXIF metadata is stripped from uploaded images for privacy, and hash verification is performed on Blossom uploads. Product spec documents cover features.md (55 tagged features), flows.md (10 user flows), wot.md (WoT integration + 5 NMP gaps), media.md (Blossom pipeline), and design/visual-system.md (tokens, typography, layout). No 'Nostr', 'relay', 'pubkey', or 'WoT' terminology appears in user-facing text; the app differentiates without mentioning Nostr. The app must have no stubs, mocks, or fake data — it must use real relays, real keys, publish actual photos, and provide real CIFilter/ColorFilter implementations.

<!-- citations: [^2aff7-38] [^2aff7-13] [^2aff7-25] [^2aff7-37] [^2aff7-57] [^2aff7-78] [^2aff7-88] [^2aff7-96] -->
## Feed & Content

The main feed defaults to the user's follows or network (global view with WoT filtering to prevent spam). A per-group view of posts is required. The app provides per-relay views in a magazine style. Image filters are available when publishing, with 12 filters: Daylight, Ember, Dusk, Mist, Chrome, Film, Fade, Grain, Arctic, Copper, Veil, Bloom.

<!-- citations: [^2aff7-14] [^2aff7-26] [^2aff7-87] -->
## Market & Differentiation

Market research must be performed to understand how people express interest in a product like this, what features they want, and the competitive landscape. Product differentiation must be identified without mentioning 'nostr'.

<!-- citations: [^2aff7-15] [^2aff7-40] -->
## Sound & Interaction Design

Sound design is silent by default with exactly 2 opt-in sounds: ShutterSoft (publish) and ZapChime. Empty states use procedural line-art breathing loops, not Lottie, across 6 empty state types. <!-- [^2aff7-27] -->

## Visual Design System

The visual design system specifies light/dark color tokens with hex values, iOS SF Pro and Android Google Sans typography, a spacing grid, feed card anatomy with precise measurements, button specs, SF Symbols ↔ Material icon mapping, shadow/elevation, and badges. The mockup includes: scrollable feed with Following/Network tabs, like with heart-burst animation, zap with amount picker, tab bar navigation, discover with suggested-user cards, notifications with grouped activity, profile with 3-column grid, avatar-tap profile modal, post detail with comments thread, compose sheet with photo picker and 12-filter strip, and a 3-new-posts pill that appears after a delay. The mockup is deployed at https://olas-mockup.vercel.app.

<!-- citations: [^2aff7-28] [^2aff7-48] [^2aff7-79] -->
