---
title: Media Server Configuration
slug: media-server-config
topic: product
summary: The default media server is blossom.primal.net, with media servers configurable from app settings
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

# Media Server Configuration

## Media Server Configuration

The default media server is blossom.primal.net, with media servers configurable from app settings. Media uploads follow the Blossom protocol (BUD-01/02/03) with JPEG 92 quality, 2048px max dimension, EXIF stripping, and SHA-256 hash verification. Blossom is used for content-addressed media storage using SHA-256 hashes, with kind 10063 for user server list and kind 24242 for auth.

<!-- citations: [^2aff7-44] [^2aff7-54] [^2aff7-74] -->
