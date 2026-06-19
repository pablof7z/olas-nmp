---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: reversal
status: superseded
subjects:
  - olas-product-direction
  - nip-29-deferred
supersedes:
  - 2026-06-19-1-pivot-from-nip-29-private-communities
related_claims: []
source_lines:
  - 1704-1708
captured_at: 2026-06-19T09:28:40Z
---

# Episode: Olas pivots from NIP-29 communities to photo-feed-first Nostr client

## Prior State

Product was conceived as a private-first instagram for small communities using NIP-29 closed-group relay semantics. Main feed would aggregate posts from all groups a user belongs to, with per-group views.

## Trigger

User directive at line 1704: 'Ok, let's pivot for now: let's just build a good pictures-feed-first client (which is what Olas has been from the beginning in its previous incarnations) — we'll add NIP-29 stuff later.'

## Decision

Olas v1 is a photo-feed-first Nostr client using WoT-filtered global/follows feeds as the primary spam defense. NIP-29 closed groups are explicitly deferred. Feed defaults to Network (WoT-filtered global) until user follows ≥15 accounts, then auto-switches to Follows. NIP-68 picture posts with imeta tags are the core content primitive; NIP-22 comments for interactions.

## Consequences

- WoT becomes primary spam filter instead of group membership gates
- Feed semantics change from per-group aggregation to follows + WoT-filtered network
- All spec documents, user flows, and interactive mockup reflect photo-feed-first design
- NIP-29 relay (olas.f7z.io) remains deployed but is not the v1 user-facing feature
- Product positioning shifts from 'private communities' to 'Instagram before it became video-first, with trust-based spam filtering'

## Open Tail

- NIP-29 closed groups to be added post-v1
- MLS encryption deferred alongside NIP-29

## Evidence

- transcript lines 1704-1708

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-olas-pivots-from-nip-29-communities.json`](transcripts/2026-06-19-1-olas-pivots-from-nip-29-communities.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-olas-pivots-from-nip-29-communities.json`](transcripts/raw/2026-06-19-1-olas-pivots-from-nip-29-communities.json)
