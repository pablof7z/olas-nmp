---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: active
subjects:
  - wot-ffi-gap
  - olas-network-feed
  - nmp-wot
supersedes:
  - 2026-06-19-3-wot-gap-handling-doctrine-no-local
related_claims: []
source_lines:
  - 2916-2934
captured_at: 2026-06-19T12:11:11Z
---

# Episode: WoT scoring unavailable: network feed shown unfiltered until upstream gap resolves

## Prior State

Olas expected to use Web of Trust scoring to filter the 'Network' tab to only show posts from trusted/extended-network contacts.

## Trigger

Discovery during NMP API audit that nmp_app_wot_score() does not exist in nmp-ffi. There is no C-ABI way for Swift/Kotlin to query a trust score for a given pubkey.

## Decision

Network tab subtitle reads 'Your extended network' with no server-side filtering applied. WoT Settings shows a 'Trust scoring is updating' note. No local workaround or scoring approximation is permitted — the Review build phase fails if one is found.

## Consequences

- Network feed is unfiltered — shows all posts from relays, not just from trusted contacts
- Feature parity constraint means Android has the same unfiltered network behavior
- 5 upstream WoT FFI gaps batch-identified but not yet filed

## Open Tail

- Upstream nmp-ffi needs nmp_app_wot_score() or equivalent batch scoring API before network feed can be WoT-filtered

## Evidence

- transcript lines 2916-2934

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-3-wot-scoring-unavailable-network-feed-shown.json`](transcripts/2026-06-19-3-wot-scoring-unavailable-network-feed-shown.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-3-wot-scoring-unavailable-network-feed-shown.json`](transcripts/raw/2026-06-19-3-wot-scoring-unavailable-network-feed-shown.json)
