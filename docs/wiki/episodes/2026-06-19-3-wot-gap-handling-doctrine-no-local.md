---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - olas-nmp-wot
  - nmp-dependency-policy
supersedes: []
related_claims: []
source_lines:
  - 1878-2066
  - 1915-1916
captured_at: 2026-06-19T11:03:46Z
---

# Episode: WoT gap handling doctrine — no local hacks, fix upstream

## Prior State

Gaps in a dependency crate would typically be worked around locally with wrapper code or inline patches

## Trigger

User directive: 'if something is inadequate or incomplete don't hack around it: file a gh issue on the NMP repo, then launch a PERT flow on the NMP codebase, land the PR and consume it on the Olas codebase: part of building this app is improving NMP itself'

## Decision

Five identified nmp-wot gaps (batch score API, result caching, configurable thresholds, graph introspection, session persistence) must each be: (1) skeptically validated by Codex before filing, (2) filed as GH issues on pablof7z/nostr-multi-platform with label area:nmp-wot, (3) fixed via PERT flow on NMP codebase, (4) consumed in Olas after NMP pin bump. No local wrappers, no hardcoded thresholds, no single-app concerns leaking into NMP.

## Consequences

- NMP crate boundaries are protected — Olas-specific logic stays in nmp-app-olas
- Identified gap list is tracked in docs/spec/wot.md (Gaps A–E) with Codex validation questions
- NMP is pinned at rev 5dd71ba5c7f9e12e98c1068201e3f12f942bdb0c (v0.8.0) until gaps are landed
- App development blocked on WoT features until upstream fixes land

## Open Tail

- None of the 5 gaps have been validated or filed yet — pending implementation start

## Evidence

- transcript lines 1878-2066
- transcript lines 1915-1916

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-3-wot-gap-handling-doctrine-no-local.json`](transcripts/2026-06-19-3-wot-gap-handling-doctrine-no-local.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-3-wot-gap-handling-doctrine-no-local.json`](transcripts/raw/2026-06-19-3-wot-gap-handling-doctrine-no-local.json)
