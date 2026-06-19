---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - olas
  - cross-platform-parity
  - agents-md
supersedes: []
related_claims: []
source_lines:
  - 1-1
  - 771-771
  - 927-931
captured_at: 2026-06-19T07:37:13Z
---

# Episode: iOS/Android strict feature-parity doctrine

## Prior State

No project existed; the implicit default for cross-platform apps is that platforms may ship features on different timelines or with different surface areas.

## Trigger

User directive: 'we'll start with an ios and android app (that MUST be kept in feature-parity at all times)' — explicit, non-negotiable constraint with MUST emphasis.

## Decision

Adopt strict feature parity as a hard architectural invariant: every PR that touches one platform must ship the equivalent change on the other in the same PR, no stubs allowed. Codified in AGENTS.md alongside the 300/500 LOC file-size rule transplanted from the NMP repo.

## Consequences

- All future PRs are gated on dual-platform implementation — a single-platform change cannot merge alone.
- Development velocity is bounded by the slower platform's implementation cycle.
- The AGENTS.md rule serves as an enforceable check for both human and agent contributors.
- The 300/500 LOC soft/hard limit was transplanted from the NMP repo, aligning code-organization norms across the NMP ecosystem.

## Open Tail

- No automated CI check for parity yet — enforcement is convention-based until a lint or review gate is added.
- The justfile and CI pipeline will need build-and-test targets for both platforms to make parity enforceable in automation.

## Evidence

- transcript lines 1-1
- transcript lines 771-771
- transcript lines 927-931

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-ios-android-strict-feature-parity-doctrine.json`](transcripts/2026-06-19-1-ios-android-strict-feature-parity-doctrine.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-ios-android-strict-feature-parity-doctrine.json`](transcripts/raw/2026-06-19-1-ios-android-strict-feature-parity-doctrine.json)
