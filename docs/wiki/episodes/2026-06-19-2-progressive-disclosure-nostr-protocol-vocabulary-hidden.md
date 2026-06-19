---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: active
subjects:
  - olas-ux-vocabulary
  - olas-progressive-disclosure
supersedes: []
related_claims: []
source_lines:
  - 1760-1766
  - 1878-2066
captured_at: 2026-06-19T11:03:46Z
---

# Episode: Progressive disclosure — Nostr protocol vocabulary hidden from users

## Prior State

Nostr clients typically expose protocol terminology (npub, nsec, relay, WoT, pubkey) to all users as the primary identity and trust vocabulary

## Trigger

User directive: 'End consumer is non-technical but should be possible to dig into more technical stuff (progressive disclosure)'

## Decision

Three-tier settings hierarchy (Everyday / Advanced / Expert). Protocol concepts get enforced human-facing names: nsec → 'Recovery Key', npub → 'account address' or 'handle', WoT → 'Who can show up in your feed' with Close/Balanced/Open presets, relays → never shown unprimed. Power surfaces on demand only.

## Consequences

- All user-facing copy in specs, mockups, and implementations must pass a no-jargon audit
- WoT filtering presented as three preset cards with live preview, never as numeric scores or pubkey distances
- Settings sheet in mockup has three disclosure tiers matching this model
- Follow packs (NIP-51 kind 30000) used as onboarding mechanism instead of asking users to understand follow graphs

## Open Tail

- Exact copy for Expert-tier settings screens not yet finalized

## Evidence

- transcript lines 1760-1766
- transcript lines 1878-2066

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-progressive-disclosure-nostr-protocol-vocabulary-hidden.json`](transcripts/2026-06-19-2-progressive-disclosure-nostr-protocol-vocabulary-hidden.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-progressive-disclosure-nostr-protocol-vocabulary-hidden.json`](transcripts/raw/2026-06-19-2-progressive-disclosure-nostr-protocol-vocabulary-hidden.json)
