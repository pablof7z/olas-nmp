---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: root-cause
status: active
subjects:
  - nmp-ffi-init-sequence
  - olas-ios-launch
supersedes:
  - 2026-06-19-2-adr-0053-nmp-app-consume-all
related_claims: []
source_lines:
  - 3690-3942
captured_at: 2026-06-19T12:26:21Z
---

# Episode: ADR-0053 projection-consumption crash root cause

## Prior State

NMPBridge.initialize() called nmp_app_start without first declaring projection-consumption intent, causing a debug_assert! panic (SIGABRT) on every app launch

## Trigger

App crashed immediately on launch; crash log showed panic inside nmp_app_start; code inspection revealed ADR-0053 requires nmp_app_consume_all_builtin_projections(app) or nmp_app_declare_consumed_projections(app, json) before start

## Decision

Added nmp_app_consume_all_builtin_projections(app) call in NMPBridge.initialize() before nmp_app_start, using the consume-all path (no custom projection list needed for Olas)

## Consequences

- All future NMP-based apps must call one of the two projection-consumption functions before nmp_app_start or they will panic in debug builds
- Stale DerivedData initially masked the fix; a clean build was needed to confirm the crash was resolved
- The olas_app.h header already exposed the function; no FFI surface change required

## Open Tail

- Confirm Android JNI init sequence also calls the equivalent function before nmp_app_start

## Evidence

- transcript lines 3690-3942

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-adr-0053-projection-consumption-crash-root.json`](transcripts/2026-06-19-1-adr-0053-projection-consumption-crash-root.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-adr-0053-projection-consumption-crash-root.json`](transcripts/raw/2026-06-19-1-adr-0053-projection-consumption-crash-root.json)
