---
type: episode-card
date: 2026-06-19
session: 2aff77b8-e8ba-493a-b944-1fea0ecd124d
transcript: /Users/pablofernandez/.claude/projects/-Users-pablofernandez-Work-Olas/2aff77b8-e8ba-493a-b944-1fea0ecd124d.jsonl
salience: product
status: active
subjects:
  - photo-upload-metadata
  - nip-52-geohash
  - olas-picture-post
supersedes: []
related_claims: []
source_lines:
  - 3944-4202
captured_at: 2026-06-19T12:26:21Z
---

# Episode: EXIF stripping and geohash-only location policy for photo posts

## Prior State

Photos could carry full EXIF metadata including GPS coordinates, camera info, and timestamps when uploaded

## Trigger

User directive: strip all EXIF data even when location toggle is enabled; for location, only add a NIP-52 'g' tag with 4-character precision (~39km×20km)

## Decision

Native encoding step uses CGImageDestination writing only kCGImageDestinationLossyCompressionQuality (zero GPS/camera/date metadata). Location toggle triggers CLLocationManager and encodes a 4-char geohash passed as a nullable parameter through the Rust FFI to olas_picture_post_publish_json, which appends ["g", geohash] to the kind:20 event tags when present

## Consequences

- User privacy guaranteed: no EXIF/GPS metadata ever leaves the device in the JPEG payload
- Location is coarse-grained at ~39km² (4-char geohash precision) per NIP-52
- olas_picture_post_publish_json C ABI signature changed from 4 to 5 parameters (added geohash); Android JNI binding was updated to pass null for the 5th arg until Android implements the geohash toggle
- CGImageDestination approach is more reliable than CGImageDestinationRemoveMetadata which can leave some EXIF intact

## Open Tail

- Android CaptionScreen needs the same location toggle UI and geohash encoding logic

## Evidence

- transcript lines 3944-4202

## Conversation

- Cleaned transcript (verbatim user words, abbreviated agent replies): [`transcripts/2026-06-19-2-exif-stripping-and-geohash-only-location.json`](transcripts/2026-06-19-2-exif-stripping-and-geohash-only-location.json)
- Raw transcript (verbatim user words, full agent replies): [`transcripts/raw/2026-06-19-2-exif-stripping-and-geohash-only-location.json`](transcripts/raw/2026-06-19-2-exif-stripping-and-geohash-only-location.json)
