---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - platform-parity
  - olas-app
supersedes: []
related_claims: []
source_lines:
  - 1-1
  - 927-930
captured_at: 2026-06-19T07:57:24Z
---

# Episode: iOS/Android feature parity enforced as non-negotiable invariant

## Prior State

No constraint existed; a new NMP app had no cross-platform enforcement policy. Previous apps (Chirp, Podcast) had iOS-first development with Android trailing.

## Trigger

User directive at session start: 'we'll start with an ios and android app (that MUST be kept in feature-parity at all times)' — explicitly raising this from aspiration to invariant.

## Decision

Feature parity is a hard rule: every PR that touches one platform must ship the equivalent change on the other in the same PR. No stubs allowed. Encoded in AGENTS.md as a non-negotiable section.

## Consequences

- No platform can ship a feature the other lacks — this gates all future PRs.
- Android must be a first-class target from day one, not a port.
- Code-sharing via the Rust FFI crate (nmp-app-olas) becomes structurally critical to avoid duplicating business logic across Swift and Kotlin.
- Future feature flags or phased rollouts must be symmetric across both platforms.

## Open Tail

- Enforcement mechanism (CI gate? PR template checklist?) not yet implemented.
- How to handle platform-specific capabilities (e.g., Widgets, Live Activities) that have no analogue on the other side.

## Evidence

- transcript lines 1-1
- transcript lines 927-930

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-ios-android-feature-parity-enforced-as.json`](transcripts/2026-06-19-1-ios-android-feature-parity-enforced-as.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-ios-android-feature-parity-enforced-as.json`](transcripts/raw/2026-06-19-1-ios-android-feature-parity-enforced-as.json)
