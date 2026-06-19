---
title: Relay Deployment
slug: relay-deployment
topic: relay
summary: The relay runs on port 12304 at 127.0.0.1 on host 157.180.102.242, deployed to /opt/olas-relay/
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

# Relay Deployment

## Deployment

The relay runs on port 12304 at 127.0.0.1 on host 157.180.102.242, deployed to /opt/olas-relay/. The relay binary is cross-compiled for Linux amd64. A systemd service named olas-relay.service manages the relay process.

The relay is served at olas.f7z.io, which points to 157.180.102.242 via DNS managed through Vercel CLI, with Caddy reverse-proxying to 127.0.0.1:12304 with auto-TLS via ACME.

<!-- citations: [^2aff7-9] [^2aff7-10] [^2aff7-30] [^2aff7-31] -->
