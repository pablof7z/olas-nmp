---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: superseded
subjects:
  - account-creation-ux
  - nip-05-deferred
  - progressive-disclosure
supersedes:
  - 2026-06-19-4-seamless-account-creation-username-olas-app
related_claims: []
source_lines:
  - 2317-2322
captured_at: 2026-06-19T11:19:30Z
---

# Episode: Seamless account creation hides Nostr key jargon behind username@olas.app

## Prior State

Standard Nostr apps expose key management (nsec/npub) to users during onboarding, requiring technical understanding of cryptographic keys.

## Trigger

User instruction: 'have support for creating an account that doesn't request technical stuff like keys and stuff; allow registering a username@olas.app (we will wire the actual nip05 registration later)'.

## Decision

Account creation presents only a display-name field and a username field with '@' prefix and '.olas.app' suffix baked into the input UI. Keypair is generated silently in background. The user never sees nsec, npub, or any cryptographic terminology. NIP-05 registration to olas.app is deferred to a later wiring phase.

## Consequences

- Onboarding mockup implements 6-step flow (Splash → Welcome → Create account → Follow packs → Media server → All set) with no key jargon anywhere
- 'Recovery Key' replaces 'nsec' in all user-facing text; 'Account address' replaces 'npub'
- NIP-05 registration infrastructure for olas.app domain is an unresolved follow-up
- Progressive disclosure: Advanced/Expert settings tiers can expose keys, but Everyday tier never does

## Open Tail

- NIP-05 registration backend for olas.app domain not yet implemented
- Key backup/recovery UX beyond 'write down this recovery key' not yet spec'd

## Evidence

- transcript lines 2317-2322

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-3-seamless-account-creation-hides-nostr-key.json`](transcripts/2026-06-19-3-seamless-account-creation-hides-nostr-key.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-3-seamless-account-creation-hides-nostr-key.json`](transcripts/raw/2026-06-19-3-seamless-account-creation-hides-nostr-key.json)
