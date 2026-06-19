---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - nmp-app-olas
  - nmp-ffi-boundary
  - olas-rust-core
supersedes:
  - 2026-06-19-1-nmp-delegation-replaces-local-reimplementation
related_claims: []
source_lines:
  - 2919-2934
captured_at: 2026-06-19T12:11:11Z
---

# Episode: NMP boundary: nmp-app-olas must not reimplement NMP-owned capabilities

## Prior State

The initial PERT workflow wrote local reimplementations: wot.rs (HashMap WoT scoring), blossom.rs (local reqwest HTTP client), account.rs (stub keypair generation), state.rs (custom handle map), jni.rs (full JNI reimplementation), and a polling loop (olas_poll_event) — all duplicating capabilities that NMP crates already own.

## Trigger

User explicit correction: 'Ensure that NMP architecture is CLOSELY followed; no hacks.' Investigation of actual nmp-ffi/nmp-blossom/nmp-wot/nmp-android-ffi APIs confirmed every reimplemented capability already exists upstream.

## Decision

nmp-app-olas reduced to a 2-file glue crate (lib.rs + jni.rs) that only adds Olas-specific registration and kind-20 interest helpers. Everything else delegates: nmp-defaults::register_defaults() wires follow/unfollow/react/zap/WoT/bootstrap; nmp-blossom is dispatched via nmp_app_dispatch_action; push model (nmp_app_set_update_callback) replaces all polling; JNI shim is pure transport to nmp-ffi symbols.

## Consequences

- No local WoT, Blossom HTTP, or crypto code permitted anywhere in the Olas codebase
- Push model mandatory — any poll loop is a boundary violation
- Review phase in the build workflow explicitly checks for and fails on local reimplementations
- WoT scoring gap (nmp_app_wot_score missing) explicitly gated: no workaround allowed, UI shows 'Your extended network' unfiltered

## Open Tail

- 5 WoT FFI gaps identified (batch scoring, caching, configurable thresholds, graph introspection, session persistence) — not yet filed as GH issues, blocked on upstream resolution

## Evidence

- transcript lines 2919-2934

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-nmp-boundary-nmp-app-olas-must.json`](transcripts/2026-06-19-1-nmp-boundary-nmp-app-olas-must.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-nmp-boundary-nmp-app-olas-must.json`](transcripts/raw/2026-06-19-1-nmp-boundary-nmp-app-olas-must.json)
