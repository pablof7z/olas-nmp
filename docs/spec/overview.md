# Olas — Product Overview

## Vision

Olas is the photo feed that Instagram was before it became a video-first advertising platform. It shows you the photographs of the people you know and trust, in the order they posted them, with no algorithm deciding who matters and no company monetizing your attention. Your media lives on servers you control, your identity is a keypair you own, and the feed is filtered by your social graph — not by engagement scores.

The product is built on Nostr. Users never need to know this.

## Who it's for

**Primary:** Anyone who wants to share photos with people they actually know — friends, collaborators, communities of interest — without putting that content inside Meta, Google, or Apple's advertising machines.

**Tone of user:** Non-technical. Appreciates beauty. Has left (or is considering leaving) Instagram because the algorithm buried their friends' posts under ads and Reels. May be a photographer who wants a real audience, or just someone who wants to see what their friends ate for breakfast in chronological order.

**Power users exist and are served:** relay operators, self-hosters, key-management sophisticates, developers. They are not the design center — but they must never hit a wall.

## What makes it different (without saying "Nostr")

1. **Your network, not our algorithm.** The feed is people you follow, in order. The wider network is filtered by who your friends trust — not by what will keep you scrolling longest.
2. **Your media, your servers.** Photos live on content-addressed storage you pick. You can move them. The app works without us.
3. **No vanity metrics by default.** Reaction counts are understated. The experience optimizes for connection, not performance anxiety.
4. **Chronological, always.** The order is time. You choose how wide to cast: just follows, or the broader trusted network.

## Core principles

| Principle | What it means in practice |
|---|---|
| **Photos first** | UI chrome is near-invisible when media is on screen. Every layout decision defers to the image. |
| **WoT as spam filter, not gatekeeper** | The default feed is the trusted network, but you can open it. Filtering is tunable, never locked. |
| **Progressive disclosure** | Non-technical users never see relay URLs, pubkeys, or "Blossom." Power users reach everything in ≤ 3 taps. |
| **Optimistic UI** | Every action (like, post, follow) appears instant. Network is eventually consistent, never blocking. |
| **iOS/Android parity** | Every feature ships on both platforms simultaneously. No platform-specific features. |
| **Rust owns logic, native renders** | All state, WoT, relay management, event validation, upload orchestration live in Rust. Swift/Kotlin render and call capabilities only. |

## Nostr event kinds used

| Kind | NIP | Purpose |
|---|---|---|
| 0 | NIP-01 | User profile (metadata) |
| 3 | NIP-02 | Follow list |
| 7 | NIP-25 | Reactions |
| 9 | NIP-09 | Deletion request |
| 20 | NIP-68 | Picture posts (primary publish kind) |
| 1111 | NIP-22 | Comments |
| 9735 | NIP-57 | Zap receipts |
| 9734 | NIP-57 | Zap requests |
| 10000 | NIP-51 | Mute list |
| 10002 | NIP-65 | Relay list |
| 10063 | BUD-03 | User media server list (Blossom) |
| 24242 | BUD-02 | Blossom auth event |

**Read compat:** kind 1 events bearing `imeta` tags are rendered as photos for ecosystem compatibility. Olas never *publishes* kind 1 photo posts.

## What is explicitly out of v1

- Private groups / DMs / communities (NIP-29 — deferred)
- Text-only posting or note microblogging
- Stories / ephemeral content
- Live video / streaming (NIP-53)
- Algorithmic ranking / recommendations
- Full post editing (caption edit only if technically feasible)
- Collaborative / multi-author posts
- Paid content / subscriptions / marketplace
- Desktop / web client
- Multi-account switching
- AI features (auto-caption, content recommendations)
- Comment threads deeper than one reply level
- Tagged-in posts tab

## Related documents

- [`docs/spec/features.md`](features.md) — Full feature spec by area
- [`docs/spec/flows.md`](flows.md) — User flows step-by-step
- [`docs/spec/wot.md`](wot.md) — WoT integration and NMP gaps
- [`docs/spec/media.md`](media.md) — Blossom / media server spec
- [`docs/design/visual-system.md`](../design/visual-system.md) — Colors, typography, spacing
- [`docs/motion/README.md`](../motion/README.md) — Animation and interaction spec
