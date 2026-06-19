---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - implementation-quality
  - photo-filters
  - feed-data
supersedes: []
related_claims: []
source_lines:
  - 2936-2954
captured_at: 2026-06-19T11:51:47Z
---

# Episode: Zero-stubs doctrine: real relays, real keys, real filters

## Prior State

The build workflow had no explicit prohibition on stubs, mocks, placeholder data, or fake implementations. The initial PERT script included placeholder Swift/Kotlin files with no constraint against hard-coded posts or stub filter implementations.

## Trigger

Session-scoped stop hook: 'ensure the whole app, both iOS and Android are working, tested (via haiku agents actually using the app) — fully polished (use opus agents that review screenshots of the app for polish feedback, pixel-perfect kind of stuff), complete working features with NO stubs, mocks, fake data, using real relays, real keys, publishing actual photos, editing the photos with cool filters and everything else expected from an instagram-like app.'

## Decision

All features must use real infrastructure: real Nostr relays via nmp-ffi, real keypairs via nmp_app_create_new_account, real Blossom uploads for photos, real CIFilter chains for all 12 named filters (Daylight, Ember, Dusk, Mist, Chrome, Film, Fade, Grain, Arctic, Copper, Veil, Bloom). A dedicated polish-test workflow was staged with an audit phase that hunts fake data, stub filters, and TODO returns, plus Haiku agents driving the simulator and Opus agents doing pixel-perfect review against visual-system.md hex values.

## Consequences

- Stub elimination audit phase added as the first step of the polish-test workflow before any simulator testing
- CIFilter chains must be real Core Image pipeline implementations, not CSS approximations
- FeedViewModel must pull from real nmp-ffi update callbacks, not hard-coded sample data
- Haiku testing via xcrun simctl driving every flow end-to-end on simulator
- Opus polish review against docs/design/visual-system.md color tokens and typography specs

## Open Tail

- Polish-test workflow (olas-polish-test.js) staged but not yet launched — waiting for build workflow completion
- Android emulator testing not yet wired (iOS simulator testing staged first)

## Evidence

- transcript lines 2936-2954

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-3-zero-stubs-doctrine-real-relays-real.json`](transcripts/2026-06-19-3-zero-stubs-doctrine-real-relays-real.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-3-zero-stubs-doctrine-real-relays-real.json`](transcripts/raw/2026-06-19-3-zero-stubs-doctrine-real-relays-real.json)
