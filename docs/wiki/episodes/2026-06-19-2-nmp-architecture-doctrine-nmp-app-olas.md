---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - nmp-app-olas
  - nmp-ffi-delegation
  - no-local-reimplementation
supersedes: []
related_claims: []
source_lines:
  - 2522-2933
captured_at: 2026-06-19T11:19:30Z
---

# Episode: NMP architecture doctrine: nmp-app-olas is a thin delegation crate, not a reimplementation

## Prior State

The PERT workflow planned to write local reimplementations: wot.rs (HashMap scoring), blossom.rs (local HTTP client), account.rs (stub keypair generation), state.rs (custom handle map), jni.rs (full JNI surface), and a poll loop (olas_poll_event) — all duplicating functionality NMP already provides.

## Trigger

User instruction: 'Ensure that NMP architecture is CLOSELY followed; no hacks.' The assistant then investigated NMP's actual API surface and discovered nmp-ffi (complete C-ABI lifecycle + identity + relay + feed + action dispatch + push callback), nmp-blossom (UploadAction), nmp-wot (WotGraph + TrustDecision + bootstrap runtime), nmp-signers (LocalKeySigner + AccountManager), and nmp-android-ffi (full JNI shim).

## Decision

nmp-app-olas contains only 4 thin functions: (1) olas_app_register() — calls nmp-defaults + nmp-blossom::register_actions(), (2) olas_open_photo_feed() — opens kind-20 interest via nmp_app_open_interest, (3) olas_open_follow_pack() — NIP-51 pack via nmp_app_open_uri, (4) olas_blossom_upload_input_json() — builds action JSON for nmp_app_dispatch_action. All business logic stays in NMP crates. Push model via nmp_app_set_update_callback, not polling. WoT gaps must be filed as upstream GH issues with Codex validation, never hacked locally.

## Consequences

- Network feed shows 'Your extended network' unfiltered until nmp_app_wot_score() is added upstream
- Review phase explicitly checks for local reimplementations and fails the build if found
- 5 WoT gaps (batch scoring, configurable thresholds, graph introspection, persistence, sparse-graph degradation) must be validated by Codex before filing NMP issues
- Any new capability needed by Olas must go through PERT on the NMP codebase first, then be consumed as a pinned dependency

## Open Tail

- nmp_app_wot_score() does not exist yet in nmp-ffi — needs upstream PR
- WoT gap issues not yet filed on NMP repo

## Evidence

- transcript lines 2522-2933

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas.json`](transcripts/2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas.json`](transcripts/raw/2026-06-19-2-nmp-architecture-doctrine-nmp-app-olas.json)
