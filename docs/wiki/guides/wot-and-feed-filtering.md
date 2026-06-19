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

WoT filtering uses nmp-wot; if something is inadequate or incomplete, gaps must be skeptically validated by Codex before filing a GitHub issue on the NMP repo, then fixed via PERT flow on the NMP codebase, then consumed in Olas — never hacked around locally. The default feed shows the user's follows, with a global view that uses WoT filtering to prevent spam. The nmp_app_wot_score() gap in nmp-ffi means the Network tab shows 'Your extended network' unfiltered until the upstream gap is resolved; no local workaround is permitted. New users default to the Network (WoT-filtered global) feed until they follow ≥15 accounts, at which point the default auto-switches to the Following feed. WoT is presented to non-technical users as 'Who can show up in your feed' with three preset cards (Close/Balanced/Open) with live preview; the terms 'WoT' or 'pubkey distance' are never exposed to users. Five NMP WoT gaps have been identified — batch scoring, session caching, configurable thresholds, graph introspection, and persistence across sessions — and must go through the Codex-validate → GH-issue → PERT-fix → pin-bump pipeline before being consumed in Olas.

<!-- citations: [^2aff7-32] [^2aff7-42] [^2aff7-70] [^2aff7-82] [^2aff7-90] [^2aff7-97] [^2aff7-104] -->
