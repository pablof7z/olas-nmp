---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: reversal
status: active
subjects:
  - olas-product-direction
  - nip-29-deferred
  - photo-feed-priority
supersedes:
  - 2026-06-19-1-pivot-from-nip-29-communities-to
related_claims: []
source_lines:
  - 1878-2066
captured_at: 2026-06-19T11:19:30Z
---

# Episode: Olas pivoted from NIP-29 group app to photo-feed-first client

## Prior State

Olas was scoped as a private-first Instagram for small communities using NIP-29 (with MLS later). The initial brainstorm asked agents to spec NIP-29 group features, per-group views, and community invites.

## Trigger

User instruction: 'Ok, let's pivot for now: let's just build a good pictures-feed-first client (which is what Olas has been from the beginning in its previous incarnations) we'll add NIP-29 stuff later.'

## Decision

Olas v1 is a photo-feed-first Nostr client. NIP-29 group/community features are explicitly deferred. Core differentiator is WoT-filtered feed (Following + Network modes). Product spec was rewritten around image posts (NIP-68 kind 20), Blossom media, and progressive disclosure.

## Consequences

- NIP-29 removed from v1 scope; group view and community invites are future work
- WoT becomes the primary spam filter for the Network feed instead of group membership
- Empty-feed problem solved by defaulting new users to Network (WoT-filtered global) until they follow ≥15 accounts, then auto-switching to Following
- Product spec documents (features, flows, wot, media) were all written against the photo-first scope

## Open Tail

- NIP-29 re-introduction timeline and scope undefined
- MLS end-to-end encryption still deferred

## Evidence

- transcript lines 1878-2066

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-olas-pivoted-from-nip-29-group.json`](transcripts/2026-06-19-1-olas-pivoted-from-nip-29-group.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-olas-pivoted-from-nip-29-group.json`](transcripts/raw/2026-06-19-1-olas-pivoted-from-nip-29-group.json)
