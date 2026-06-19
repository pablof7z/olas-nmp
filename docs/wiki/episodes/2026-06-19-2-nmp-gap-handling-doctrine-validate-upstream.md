---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - nmp-wot-boundaries
  - olas-wot-integration
supersedes:
  - 2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must
related_claims: []
source_lines:
  - 1706-1707
captured_at: 2026-06-19T09:28:40Z
---

# Episode: NMP gap-handling doctrine: validate upstream, never hack around

## Prior State

No explicit policy on how Olas should handle missing or inadequate functionality in NMP crates. In other projects (e.g., Chirp), app-specific workarounds may have been embedded locally.

## Trigger

User directive at line 1706: 'if something is inadequate or incomplete don't hack around it: file a gh issue on the NMP repo, then launch a PERT flow on the NMP codebase in this computer, land the PR and consume it on the Olas codebase — gaps MUST BE skeptically validated by codex: we want to avoid bloating the concerns of NMP and protecting the boundaries of NMP from single-app concerns'

## Decision

Gaps in NMP (especially WoT: batch scoring, session caching, configurable thresholds, graph introspection, persistence) must be skeptically validated by Codex before any issue is filed, then fixed upstream via PERT flow, then consumed in Olas. NMP boundaries are protected from single-app concerns.

## Consequences

- Olas development may block on NMP improvements — this is accepted
- Identified 5 specific WoT gaps (Gaps A–E) in the spec, each with a Codex validation question
- No app-specific WoT wrappers or patches in the Olas codebase
- PERT flow on NMP codebase becomes a standard operational pattern for this project

## Open Tail

- Gap A (batch score API) through Gap E (persistence) need Codex validation and GH issue filing when implementation begins

## Evidence

- transcript lines 1706-1707

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream.json`](transcripts/2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream.json`](transcripts/raw/2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream.json)
