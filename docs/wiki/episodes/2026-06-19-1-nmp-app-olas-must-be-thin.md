---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - nmp-app-olas-ffi-architecture
  - nmp-delegation-doctrine
supersedes:
  - 2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas
related_claims: []
source_lines:
  - 2522-2670
  - 2671-2811
  - 2891-2934
captured_at: 2026-06-19T11:38:27Z
---

# Episode: nmp-app-olas must be thin FFI glue — no local reimplementations of NMP crates

## Prior State

The PERT workflow planned to write local reimplementations inside nmp-app-olas: wot.rs (HashMap-based trust scoring), blossom.rs (HTTP upload client), account.rs (stub keypair generation), state.rs (custom handle map), jni.rs (full JNI surface from scratch), and a poll-based event loop (olas_poll_event). This would have duplicated logic NMP already owns.

## Trigger

User directive 'Ensure that NMP architecture is CLOSELY followed; no hacks' prompted investigation of NMP crate APIs, revealing that nmp-ffi, nmp-blossom, nmp-wot, nmp-signers, and nmp-android-ffi already provide complete implementations of all planned local stubs.

## Decision

nmp-app-olas is reduced to a thin registration + interest-definition crate with only 4 Olas-specific FFI additions: olas_app_register (calls nmp-defaults + nmp-blossom::register_actions), olas_open_photo_feed (kind-20 interest via nmp_app_open_interest), olas_open_follow_pack (NIP-51 via nmp_app_open_uri), olas_blossom_upload_input_json (action JSON builder). All business logic stays in NMP crates. Push model via nmp_app_set_update_callback replaces the planned poll loop. The running workflow was stopped and rewritten.

## Consequences

- WoT score API gap confirmed: nmp_app_wot_score() does not exist in nmp-ffi C-ABI — network feed shows 'Your extended network' unfiltered until upstream gap is resolved; any local workaround would violate doctrine
- Blossom uploads dispatch through nmp_app_dispatch_action('nmp.blossom.upload', ...) — no local HTTP client needed
- Account creation flows through nmp_app_create_new_account — no local crypto stub needed
- Android JNI shim in nmp-app-olas/jni.rs wraps nmp-ffi symbols only; GlobalRef JVM listener for push delivery, catch_unind on every function
- The 5 previously identified WoT gaps (batch score API, configurable thresholds, graph introspection, persistence, sparse-graph degradation) must be resolved upstream in nmp-ffi before the app can filter by trust

## Open Tail

- nmp_app_wot_score() upstream gap must be filed (after Codex validation per wot.md process) before network-feed trust filtering can work
- Polish-test workflow staged but not yet running — will audit for any remaining stubs, fake data, or doctrine violations once the build workflow completes

## Evidence

- transcript lines 2522-2670
- transcript lines 2671-2811
- transcript lines 2891-2934

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-nmp-app-olas-must-be-thin.json`](transcripts/2026-06-19-1-nmp-app-olas-must-be-thin.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-nmp-app-olas-must-be-thin.json`](transcripts/raw/2026-06-19-1-nmp-app-olas-must-be-thin.json)
