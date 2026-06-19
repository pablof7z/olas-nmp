---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: superseded
subjects:
  - nmp-app-olas
  - rust-ffi-layer
  - nmp-ffi
supersedes: []
related_claims: []
source_lines:
  - 2522-2934
captured_at: 2026-06-19T11:51:47Z
---

# Episode: NMP delegation replaces local reimplementation

## Prior State

The PERT workflow's Rust Core phase planned to write local stub reimplementations: wot.rs (HashMap scoring), blossom.rs (HTTP client), account.rs (keypair generation), state.rs (custom handle map), and a full JNI reimplementation — duplicating functionality NMP already owns. A poll-based event loop (olas_poll_event) was also planned.

## Trigger

User directive 'Ensure that NMP architecture is CLOSELY followed; no hacks' combined with discovery that nmp-ffi already provides the complete C-ABI surface (lifecycle, identity, relay, feed, action dispatch, push callback), nmp-blossom provides UploadAction, nmp-wot provides WotGraph/TrustDecision bootstrap, nmp-signers provides LocalKeySigner/AccountManager, and nmp-android-ffi provides the JNI pattern.

## Decision

nmp-app-olas becomes a thin registration + interest-definition crate only. It exposes exactly three Olas-specific symbols: olas_app_register() (wires nmp-defaults + nmp-blossom), olas_open_photo_feed() (kind-20 interest via nmp_app_open_interest), and olas_open_follow_pack() (NIP-51 pack via nmp_app_open_uri). Swift/Kotlin call nmp-ffi symbols directly for all standard operations. Push model via nmp_app_set_update_callback replaces the planned poll loop. JNI shim follows nmp-android-ffi's GlobalRef pattern exactly.

## Consequences

- WoT score gap explicitly gated: nmp_app_wot_score() does not exist in nmp-ffi, so Network feed shows 'Your extended network' unfiltered until the upstream gap is filed and resolved — no workaround allowed
- Blossom upload flows through nmp_app_dispatch_action('nmp.blossom.upload', ...) rather than a local HTTP client
- Account creation calls nmp_app_create_new_account directly; no local keypair code
- The existing stopped workflow was replaced with a corrected one (wa8cgxtbi) that enforces this architecture

## Open Tail

- Upstream nmp-ffi needs a WoT score FFI entry point; until then the Network feed cannot filter by trust
- NIP-05 registration for username@olas.app deferred to future wiring

## Evidence

- transcript lines 2522-2934

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-nmp-delegation-replaces-local-reimplementation.json`](transcripts/2026-06-19-1-nmp-delegation-replaces-local-reimplementation.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-nmp-delegation-replaces-local-reimplementation.json`](transcripts/raw/2026-06-19-1-nmp-delegation-replaces-local-reimplementation.json)
