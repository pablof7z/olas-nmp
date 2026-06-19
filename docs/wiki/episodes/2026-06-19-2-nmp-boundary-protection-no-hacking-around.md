---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - nmp-boundaries
  - wot-gaps
  - pert-flow
supersedes:
  - 2026-06-19-2-nmp-gap-handling-doctrine-validate-upstream
related_claims: []
source_lines:
  - 1706-1707
captured_at: 2026-06-19T10:36:00Z
---

# Episode: NMP boundary protection: no hacking around gaps

## Prior State

No established policy for handling gaps in NMP crates when building an app on top. The default risk was that app-specific workarounds would leak into NMP, or that gaps would be papered over with hacks in the Olas codebase.

## Trigger

User directive: 'if something is inadequate or incomplete don't hack around it: file a gh issue on the NMP repo, then launch a PERT flow on the NMP codebase… gaps MUST BE skeptically validated by codex: we want to avoid bloating the concerns of NMP and protecting the boundaries of NMP from single-app concerns it's important'

## Decision

Mandatory 4-step pipeline for any NMP gap: (1) Codex skeptically validates the gap is real and not an app-specific concern, (2) GH issue filed on NMP repo, (3) PERT flow (Codex plan → Sonnet execute → Opus review → Haiku test) lands the fix upstream, (4) Olas consumes the upstream fix. No hacks, no workarounds, no bloating NMP with single-app concerns.

## Consequences

- 5 WoT gaps identified and scoped with Codex validation questions: batch score API, result caching, configurable thresholds, graph introspection, session persistence
- Each gap requires Codex validation before any GH issue can be filed
- Building the app and improving NMP are one process, not separate tracks
- AGENTS.md codifies this as enforceable project doctrine

## Open Tail

- None of the 5 WoT gaps have been filed as GH issues yet — blocked on implementation start

## Evidence

- transcript lines 1706-1707

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-nmp-boundary-protection-no-hacking-around.json`](transcripts/2026-06-19-2-nmp-boundary-protection-no-hacking-around.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-nmp-boundary-protection-no-hacking-around.json`](transcripts/raw/2026-06-19-2-nmp-boundary-protection-no-hacking-around.json)
