---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: root-cause
status: superseded
subjects:
  - nmp-ffi-projections
  - nmp-bridge-init
  - olas-ios-startup
supersedes: []
related_claims: []
source_lines:
  - 3523-3693
captured_at: 2026-06-19T12:11:11Z
---

# Episode: ADR-0053: nmp_app_consume_all_builtin_projections required before nmp_app_start

## Prior State

NMPBridge.initialize() called nmp_app_start directly without declaring which projections the host consumes, assuming NMP would default to emitting everything.

## Trigger

iOS app crashed on launch with SIGABRT — Rust debug_assert! panicked inside nmp_app_start because consumed_projections_are_undeclared() was true. Crash stack: nmp_app_start → NMPBridge.initialize().

## Decision

Add nmp_app_consume_all_builtin_projections(app) call in NMPBridge.initialize() between olas_app_register and nmp_app_start, matching ADR-0053's requirement that every host must explicitly opt in before starting the kernel.

## Consequences

- App launches successfully instead of crashing
- Any future NMP-based app must call consume_all_builtin_projections or declare_consumed_projections before nmp_app_start
- ola_app.h already exported the symbol — no header change needed

## Open Tail

*(none)*

## Evidence

- transcript lines 3523-3693

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-adr-0053-nmp-app-consume-all.json`](transcripts/2026-06-19-2-adr-0053-nmp-app-consume-all.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-adr-0053-nmp-app-consume-all.json`](transcripts/raw/2026-06-19-2-adr-0053-nmp-app-consume-all.json)
