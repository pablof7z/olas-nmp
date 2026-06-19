---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: reversal
status: superseded
subjects:
  - olas-directory-layout
  - monorepo-structure
supersedes: []
related_claims: []
source_lines:
  - 952-1134
captured_at: 2026-06-19T07:52:21Z
---

# Episode: Olas layout restructured from root-level dirs to apps/ monorepo pattern

## Prior State

Assistant scaffolded ios/, android/, and relay/ at the repo root, mirroring the existing NMP (Chirp) layout where ios/ and android/ sit alongside crates/ and apps/.

## Trigger

User explicitly corrected the layout (line 952–954): 'ios and android should be in apps/{ios,android}. relay in apps/{relay}'

## Decision

All platform directories moved under apps/: apps/ios/, apps/android/, apps/relay/. Path references in justfile, .gitignore, build.gradle.kts, and Cargo.toml were updated to match.

## Consequences

- Consistent monorepo convention: every deployable target lives under apps/
- justfile cargo-ndk output path changed to apps/android/...; .gitignore glob patterns updated from android/ to apps/android/
- iOS XcodeGen project.yml LIBRARY_SEARCH_PATHS still resolve correctly (../../target/ from apps/ios/)
- Future additions (e.g. apps/web) follow the established pattern without root-level clutter

## Open Tail

*(none)*

## Evidence

- transcript lines 952-1134

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-1-olas-layout-restructured-from-root-level.json`](transcripts/2026-06-19-1-olas-layout-restructured-from-root-level.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-1-olas-layout-restructured-from-root-level.json`](transcripts/raw/2026-06-19-1-olas-layout-restructured-from-root-level.json)
