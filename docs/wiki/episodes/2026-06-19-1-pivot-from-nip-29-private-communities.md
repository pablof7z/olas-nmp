---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: reversal
status: superseded
subjects:
  - olas-product-direction
  - nip-29-deferral
  - wot-feed-filtering
supersedes: []
related_claims: []
source_lines:
  - 1704-1708
captured_at: 2026-06-19T09:13:04Z
---

# Episode: Pivot from NIP-29 private communities to photo-feed-first Nostr client

## Prior State

Olas was conceived as a private-first Instagram for small communities (families, closed groups) built on NIP-29 group relay architecture, with per-group views as the primary feed model and MLS encryption as a future addition.

## Trigger

User directive: 'let's pivot for now: let's just build a good pictures-feed-first client (which is what Olas has been from the beginning in its previous incarnations) we'll add NIP-29 stuff later.'

## Decision

Olas v1 is a public photo-feed-first Nostr client with WoT-filtered spam prevention. NIP-29 group/community features are deferred to a later version. The primary feed shows posts from followed accounts or the user's network (global view with WoT filtering). Per-relay magazine-style views are a secondary feature. Product spec, UX flows, motion docs, and visual system all reflect the photo-first direction.

## Consequences

- All spec documents (overview, features, flows, wot, media, motion, visual system) are written for the photo-feed-first model, not the private-groups model
- NIP-29 group relay (Croissant) deployed at olas.f7z.io remains as infrastructure but has no v1 product surface
- New-user empty-feed problem solved by defaulting to Network (WoT-filtered global) until ≥15 follows, then switching to Follows feed
- WoT concept surfaced to users as 'Who can show up in your feed' with three presets (Close/Balanced/Open), never exposing Nostr vocabulary
- v1 exclusions list explicitly excludes: stories, DMs, groups/channels, live streaming, algorithmic feed, content recommendation

## Open Tail

- NIP-29 community features will need their own spec when revisited
- MLS encryption for private groups remains a future possibility

## Evidence

- transcript lines 1704-1708

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-pivot-from-nip-29-private-communities.json`](transcripts/2026-06-19-1-pivot-from-nip-29-private-communities.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-pivot-from-nip-29-private-communities.json`](transcripts/raw/2026-06-19-1-pivot-from-nip-29-private-communities.json)
