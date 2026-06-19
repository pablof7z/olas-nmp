---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: superseded
subjects:
  - olas-onboarding
  - olas-account-creation
  - nip-05
supersedes: []
related_claims: []
source_lines:
  - 2317-2323
captured_at: 2026-06-19T11:03:46Z
---

# Episode: Seamless account creation — username@olas.app, no key jargon

## Prior State

Nostr account creation requires users to understand and manage keypairs (npub/nsec), which is the standard UX across all Nostr clients

## Trigger

User directive: 'have support for creating an account that doesn't request technical stuff like keys and stuff; allow registering a username@olas.app (we will wire the actual nip05 registration later)'

## Decision

Keypair generated silently in background during account creation. User sees only two fields: display name and username (with @ prefix and .olas.app suffix shown inline in the input field). NIP-05 registration deferred to later wiring. Onboarding flow: Splash → Welcome → Create account (username@olas.app) → Follow packs → Media server → Done.

## Consequences

- Onboarding mockup implements username field with fixed .olas.app suffix — no key terminology anywhere
- NIP-05/username registration backend not yet built — mockup shows UI only
- nsec is presented as 'Recovery Key' only in Expert settings tier, never during onboarding
- NIP-51 kind 30000 follow packs anchor the follow-discovery step of onboarding instead of manual pubkey entry

## Open Tail

- NIP-05 registration server for olas.app domain not yet implemented
- Key backup/recovery flow after onboarding not yet specified

## Evidence

- transcript lines 2317-2323

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-4-seamless-account-creation-username-olas-app.json`](transcripts/2026-06-19-4-seamless-account-creation-username-olas-app.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-4-seamless-account-creation-username-olas-app.json`](transcripts/raw/2026-06-19-4-seamless-account-creation-username-olas-app.json)
