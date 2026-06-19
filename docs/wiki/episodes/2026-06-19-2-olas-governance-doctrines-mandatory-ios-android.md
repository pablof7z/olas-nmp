---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - olas-agents-md
  - feature-parity-doctrine
  - loc-limits
supersedes:
  - 2026-06-19-1-ios-android-strict-feature-parity-doctrine
related_claims: []
source_lines:
  - 1-1
  - 771-771
  - 929-930
captured_at: 2026-06-19T07:52:21Z
---

# Episode: Olas governance doctrines: mandatory iOS/Android parity and 300/500 LOC limits

## Prior State

No project governance rules existed for the new Olas repo; the NMP repo already enforced a 300/500 LOC soft/hard limit per source file.

## Trigger

User directive at line 1: 'we'll start with an ios and android app (that MUST be kept in feature-parity at all times) — add an AGENTS.md rule like the one in nmp repo about 300/500 LOC soft/hard limit per source code file'

## Decision

AGENTS.md was written with two non-negotiable doctrines: (1) every PR that touches one platform must ship the equivalent change on the other in the same PR, no stubs allowed; (2) 300-line soft / 500-line hard LOC limits per source file, mirroring the NMP repo rule.

## Consequences

- PRs lacking cross-platform equivalence are blocked by policy
- Files exceeding 500 LOC must be refactored before merge
- CLAUDE.md defers to AGENTS.md as the single source of truth (matching NMP convention)
- WIP.md is gitignored and must never be committed

## Open Tail

*(none)*

## Evidence

- transcript lines 1-1
- transcript lines 771-771
- transcript lines 929-930

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-olas-governance-doctrines-mandatory-ios-android.json`](transcripts/2026-06-19-2-olas-governance-doctrines-mandatory-ios-android.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-olas-governance-doctrines-mandatory-ios-android.json`](transcripts/raw/2026-06-19-2-olas-governance-doctrines-mandatory-ios-android.json)
