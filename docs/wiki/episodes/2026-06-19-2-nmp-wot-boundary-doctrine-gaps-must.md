---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - nmp-wot-boundary
  - olas-dependency-policy
supersedes: []
related_claims: []
source_lines:
  - 1704-1708
captured_at: 2026-06-19T09:13:04Z
---

# Episode: NMP WoT boundary doctrine: gaps must be fixed upstream, not hacked around

## Prior State

No explicit policy on how Olas should handle gaps or inadequacies in NMP's WoT crate (nmp-wot). The default behavior would be to write app-specific wrapper code or workarounds in the Olas codebase.

## Trigger

User directive: 'if something is inadequate or incomplete don't hack around it: file a gh issue on the NMP repo, then launch a PERT flow on the NMP codebase in this computer, land the PR and consume it on the Olas codebase — gaps MUST BE skeptically validated by codex: we want to avoid bloating the concerns of NMP and protecting the boundaries of NMP from single-app concerns'

## Decision

WoT gaps are never hacked around in Olas. The mandatory process is: (1) Codex skeptical validation of the gap, (2) file GH issue on NMP repo, (3) PERT flow on NMP codebase (Codex plans → Sonnet executes → Opus reviews → Haiku tests), (4) land the PR in NMP, (5) consume the new capability in Olas. NMP boundaries must be protected from single-app concerns.

## Consequences

- Five rigorously scoped NMP gaps identified in docs/spec/wot.md (Gaps A–E): batch score API, result caching, configurable thresholds, graph introspection API, persistence across sessions — each with a Codex validation question before any issue is filed
- Olas codebase must not contain WoT workarounds or app-specific WoT logic
- Building Olas is explicitly also about improving NMP itself and finding its gaps
- The PERT multi-agent pattern defined in CLAUDE.md is the prescribed execution method for landing these fixes

## Open Tail

- None of the five WoT gaps have GH issues filed yet — they await Codex validation before filing
- Actual implementation of Olas hasn't started, so the gap-filing process hasn't been exercised

## Evidence

- transcript lines 1704-1708

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must.json`](transcripts/2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must.json`](transcripts/raw/2026-06-19-2-nmp-wot-boundary-doctrine-gaps-must.json)
