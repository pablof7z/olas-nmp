---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: reversal
status: active
subjects:
  - project-layout
  - olas-repo-structure
supersedes:
  - 2026-06-19-1-olas-layout-restructured-from-root-level
related_claims: []
source_lines:
  - 953-956
  - 959-1120
captured_at: 2026-06-19T07:57:24Z
---

# Episode: Project layout restructured to apps/ monorepo convention

## Prior State

Initial scaffold placed platform directories at repo root: ios/, android/, relay/ — mirroring the existing nostr-multi-platform repo structure where ios/ and android/ are top-level.

## Trigger

User correction: 'git init, and ios and android should be in apps/{ios,android}. relay in apps/{relay}' — rejecting the top-level layout immediately after the initial commit.

## Decision

All app targets live under apps/: apps/ios/, apps/android/, apps/relay/, apps/olas/nmp-app-olas/. The Rust workspace Cargo.toml and justfile were updated to reflect the new paths. .gitignore patterns were corrected.

## Consequences

- ios/ and android/ gitignore patterns rewritten to apps/ios/ and apps/android/.
- Android build.gradle.kts jniLibs path updated to apps/android/app/src/main/jniLibs/.
- justfile commands reference apps/ paths (cargo ndk --manifest-path apps/olas/nmp-app-olas/Cargo.toml, -o apps/android/app/src/main/jniLibs).
- iOS project.yml SRCROOT now resolves from apps/ios/ — relative paths to Rust target/ still work at ../../target/.
- Sets a structural precedent: any future app targets (e.g., apps/web/) follow the same convention.

## Open Tail

*(none)*

## Evidence

- transcript lines 953-956
- transcript lines 959-1120

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-project-layout-restructured-to-apps-monorepo.json`](transcripts/2026-06-19-2-project-layout-restructured-to-apps-monorepo.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-project-layout-restructured-to-apps-monorepo.json`](transcripts/raw/2026-06-19-2-project-layout-restructured-to-apps-monorepo.json)
