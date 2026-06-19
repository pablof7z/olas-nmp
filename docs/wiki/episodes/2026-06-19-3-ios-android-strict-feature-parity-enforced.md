---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - ios-android-parity
  - platform-invariant
  - rust-owns-logic
supersedes:
  - 2026-06-19-1-ios-android-feature-parity-enforced-as
related_claims: []
source_lines:
  - 1-1
captured_at: 2026-06-19T10:36:00Z
---

# Episode: iOS/Android strict feature parity enforced via Rust-owns-logic architecture

## Prior State

No parity constraint existed for Olas. The NMP parent repo has both iOS (Chirp) and Android targets but no explicit same-PR parity rule. The prior concept was just 'we'll start with an ios and android app' with no architectural enforcement mechanism.

## Trigger

User's opening directive: 'we'll start with an ios and android app (that MUST be kept in feature-parity at all times)'

## Decision

Every PR must include equivalent changes on both platforms in the same PR — no stubs, no 'TODO: Android' placeholders. Rust owns all business logic; native layers (Swift/Kotlin) only render and call capabilities. This is codified in AGENTS.md as a non-negotiable rule.

## Consequences

- Rust-owns-logic is the only viable way to maintain strict parity without doubling every feature implementation
- All business logic flows through nmp-app-olas (Rust staticlib) via FFI; Swift and Kotlin are thin rendering shells
- The justfile provides build targets for both platforms that must be kept in sync (rust-ios-sim, rust-android, build-ios-sim, build-android)
- Any feature that cannot be expressed in Rust first is architecturally blocked until it can be
- The 300/500 LOC soft/hard file-size limit from NMP's AGENTS.md was also adopted, ensuring parity files stay small

## Open Tail

*(none)*

## Evidence

- transcript lines 1-1

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-3-ios-android-strict-feature-parity-enforced.json`](transcripts/2026-06-19-3-ios-android-strict-feature-parity-enforced.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-3-ios-android-strict-feature-parity-enforced.json`](transcripts/raw/2026-06-19-3-ios-android-strict-feature-parity-enforced.json)
