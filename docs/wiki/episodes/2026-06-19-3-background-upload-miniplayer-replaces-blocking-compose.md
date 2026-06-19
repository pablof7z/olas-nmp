---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: active
subjects:
  - upload-ux-flow
  - upload-queue
  - olas-compose
supersedes: []
related_claims: []
source_lines:
  - 3945-4210
captured_at: 2026-06-19T12:26:21Z
---

# Episode: Background upload miniplayer replaces blocking compose sheet

## Prior State

Upload flow blocked the user in the compose sheet until the entire upload+publish sequence completed

## Trigger

User directive: upload should fade to the background like a miniplayer with progress bar, allowing continued app use

## Decision

UploadQueue singleton (@Observable @MainActor) enqueues the upload and dismisses the compose sheet immediately. UploadMiniPlayer view floats above the tab bar with thumbnail, status label, and animated progress bar. Progress fractions: encoding=0.1, uploading(p)=0.1+0.7*p, publishing=0.85, done=1.0. Auto-clears active upload 2.5s after completion.

## Consequences

- User can browse feed, search, and navigate tabs while upload proceeds
- UploadQueue is a process-wide singleton, surviving tab switches and sheet dismissals
- ComposeNavigator no longer owns an UploadViewModel — upload state moved out of sheet scope
- Miniplayer transitions use .move(edge: .bottom).combined(with: .opacity) with .olasStandard animation

## Open Tail

- Multi-image upload queue (currently single-image); error retry UI in miniplayer

## Evidence

- transcript lines 3945-4210

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-3-background-upload-miniplayer-replaces-blocking-compose.json`](transcripts/2026-06-19-3-background-upload-miniplayer-replaces-blocking-compose.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-3-background-upload-miniplayer-replaces-blocking-compose.json`](transcripts/raw/2026-06-19-3-background-upload-miniplayer-replaces-blocking-compose.json)
