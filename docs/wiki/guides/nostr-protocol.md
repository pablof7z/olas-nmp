---
title: Nostr Protocol
slug: nostr-protocol
topic: product
summary: The app uses NIP-68 (kind 20) for picture events with imeta tags containing url, x (SHA-256), m (MIME), dim, blurhash, and alt
tags:
  - capture
volatility: warm
confidence: medium
created: 2026-06-19
updated: 2026-06-19
verified: 2026-06-19
compiled-from: conversation
sources:
  - session:2aff77b8-e8ba-493a-b944-1fea0ecd124d
---

# Nostr Protocol

## Event Kinds

The app uses NIP-68 (kind 20) for picture events with imeta tags containing url, x (SHA-256), m (MIME), dim, blurhash, and alt. Comments use NIP-22 (kind 1111). Zaps use NIP-57 (kinds 9734/9735).

<!-- citations: [^2aff7-23] [^2aff7-55] [^2aff7-65] [^2aff7-76] -->
## Media Uploads

Media uploads follow the Blossom protocol with BUD-01/02/03, using JPEG 92 quality, 2048px max dimension, EXIF stripping, and SHA-256 hash verification. EXIF data is always stripped from uploaded photos; when the user enables the location toggle, only a 4-character-precision geohash 'g' tag is added to the Nostr event (no raw GPS coordinates or camera metadata).

<!-- citations: [^2aff7-66] [^2aff7-101] -->
