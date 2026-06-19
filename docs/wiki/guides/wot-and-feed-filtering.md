---
title: WoT and Feed Filtering
slug: wot-and-feed-filtering
topic: product
summary: WoT filtering uses nmp-wot; if something is inadequate or incomplete, gaps must be skeptically validated by Codex before filing a GitHub issue on the NMP repo,
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

# WoT and Feed Filtering

## WoT and Feed Filtering

WoT filtering uses nmp-wot; if something is inadequate or incomplete, gaps must be skeptically validated by Codex before filing a GitHub issue on the NMP repo, then fixed via PERT flow on the NMP codebase, then consumed in Olas. The default feed shows the user's follows, with a global view that filters kind:20 Network posts through the Olas Rust app crate before native view models are built. That per-event feed filtering path consumes the nmp-wot runtime registered by nmp-defaults; the broader product-grade WoT surfaces — batch scoring, session caching, configurable thresholds, graph introspection, and persistence across sessions — are tracked in pablof7z/nostr-multi-platform#1623 and must go through the PERT-fix -> pin-bump pipeline before being consumed in Olas. New users default to the Network feed until they follow >=15 accounts, at which point the default auto-switches to the Following feed. WoT is presented to non-technical users as "Who can show up in your feed" with three preset cards (Close/Balanced/Open) with live preview; the terms "WoT" or "pubkey distance" are never exposed to users.

<!-- citations: [^2aff7-32] [^2aff7-42] [^2aff7-70] [^2aff7-82] [^2aff7-90] [^2aff7-97] [^2aff7-104] -->
