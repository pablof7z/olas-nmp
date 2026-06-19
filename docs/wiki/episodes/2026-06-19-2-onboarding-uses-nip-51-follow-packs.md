---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: active
subjects:
  - onboarding
  - nip-51
  - account-creation
supersedes:
  - 2026-06-19-3-seamless-account-creation-hides-nostr-key
related_claims: []
source_lines:
  - 2317-2332
captured_at: 2026-06-19T11:51:47Z
---

# Episode: Onboarding uses NIP-51 follow packs with hidden key jargon

## Prior State

The interactive mockup had no onboarding flow — the user explicitly identified it as missing ('It's missing the onboarding flow'). No account creation UX existed; no follow-pack mechanism was defined.

## Trigger

User directive: 'Build [the onboarding flow]. Leverage follow and media follow packs (there's a nip for them, look it up). Anchor in what's technically possible too. Also, have support for creating an account that doesn't request technical stuff like keys and stuff; allow registering a username@olas.app.'

## Decision

6-step onboarding flow: Splash (0.8s auto-advance) → Welcome (mosaic background, Create/Sign in CTAs) → Create account (display name + username@olas.app with @ prefix and .olas.app suffix baked into the input; keypair generated silently in background, never exposed) → Follow packs (5 NIP-51 kind-30000 curated cards with toggle switches — Visual Storytellers, World Travelers, Digital Artists, Food & Culture, Nostr Builders) → Media server selection (Blossom) → All set. Protocol vocabulary (Nostr, relay, pubkey, WoT) is never shown to users.

## Consequences

- NIP-51 kind 30000 (Categorized People Lists) identified as the technical anchor for onboarding follow packs
- Empty feed for new users defaults to Network (WoT-filtered global) seeded from follow packs until ≥15 follows, then auto-switches to Following
- NIP-05 registration for the .olas.app domain is deferred — username field shows the suffix but backend wiring is future work
- Trust indicators shown as 'Followed by Ana + 3 others' never as numeric scores or pubkeys

## Open Tail

- Actual NIP-05 registration endpoint for username@olas.app not yet wired
- Follow pack curation and hosting (who publishes the kind 30000 events) needs a source strategy

## Evidence

- transcript lines 2317-2332

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-onboarding-uses-nip-51-follow-packs.json`](transcripts/2026-06-19-2-onboarding-uses-nip-51-follow-packs.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-onboarding-uses-nip-51-follow-packs.json`](transcripts/raw/2026-06-19-2-onboarding-uses-nip-51-follow-packs.json)
