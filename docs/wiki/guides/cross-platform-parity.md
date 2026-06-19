---
title: Cross-Platform Parity
slug: cross-platform-parity
topic: nmp-app
summary: Olas is an NMP-based app (../nostr-multi-platform) that MUST keep iOS and Android in feature-parity at all times â every PR touching one platform must ship th
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

# Cross-Platform Parity

## Feature Parity

Olas is an NMP-based app (../nostr-multi-platform) that MUST keep iOS and Android in feature-parity at all times — every PR touching one platform must ship the equivalent change on the other in the same PR, no stubs allowed. The session-scoped goal requires the entire app (both iOS and Android) to be working, tested via Haiku agents on simulator/emulator, polished via Opus screenshot review, with zero stubs, mocks, or fake data. The PERT workflow for the actual iOS and Android build runs both platforms in parallel to feature completeness.

<!-- citations: [^2aff7-7] [^2aff7-11] [^2aff7-18] [^2aff7-52] [^2aff7-62] [^2aff7-84] -->
