---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: architecture
status: active
subjects:
  - upload-pipeline-parity
  - nmp-boundary
  - olas-ffi
supersedes: []
related_claims: []
source_lines:
  - 3950-4210
  - 4304-4315
captured_at: 2026-06-19T12:26:21Z
---

# Episode: iOS upload pipeline unified to canonical Rust dispatch path

## Prior State

iOS UploadViewModel computed SHA-256 locally via CommonCrypto CC_SHA256, assembled imeta tag strings in Swift, constructed the kind:20 event JSON in Swift (buildKind20Event), and called NMPBridge.signEvent → nmp_app_sign_event_for_return. Android dispatched nmp.blossom.upload then nmp.publish through Rust FFI. Two entirely different code paths for the same feature, violating AGENTS.md 'Rust owns all logic' and the non-negotiable iOS/Android feature-parity rule.

## Trigger

PERT architecture review identified critical parity break and NMP boundary violation: iOS reimplemented hashing, imeta construction, and event assembly that nmp-blossom and nmp.publish already own. The feature work (EXIF stripping, miniplayer) required touching the upload pipeline, giving an opportunity to correct the architecture.

## Decision

Rewrote iOS upload path to match Android's canonical Rust dispatch pattern: ImageEncoder (stateless enum) only downsizes + strips EXIF and writes temp file. UploadQueue dispatches nmp.blossom.upload action (Rust hashes, uploads, returns BUD-02 descriptor) then nmp.publish action (Rust constructs imeta, signs, routes via NIP-65). No local hashing, no event JSON construction, no signing in Swift.

## Consequences

- Single source of truth for hashing, event construction, and signing lives in Rust; both platforms share identical FFI dispatch pattern
- UploadStep state machine unified: ENCODING → UPLOADING(progress) → PUBLISHING → DONE | ERROR
- CommonCrypto import and sha256Hex function removed from iOS
- olas_picture_post_publish_json FFI function is the sole entry point for kind:20 publish input construction on both platforms
- UploadViewModel replaced by stateless ImageEncoder; state moved to UploadQueue singleton

## Open Tail

- Verify Android JNI binding passes geohash param (currently null) once Android implements the location toggle

## Evidence

- transcript lines 3950-4210
- transcript lines 4304-4315

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-5-ios-upload-pipeline-unified-to-canonical.json`](transcripts/2026-06-19-5-ios-upload-pipeline-unified-to-canonical.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-5-ios-upload-pipeline-unified-to-canonical.json`](transcripts/raw/2026-06-19-5-ios-upload-pipeline-unified-to-canonical.json)
