---
title: Architecture
slug: architecture
topic: nmp-app
summary: Rust owns all business logic; Swift and Kotlin only render UI and call capabilities through the NMP bridge
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

# Architecture

## Core Architecture

Rust owns all business logic; Swift and Kotlin only render UI and call capabilities through the NMP bridge. NMP architecture must be closely followed with no hacks — no local reimplementation of capabilities NMP already provides (WoT scoring, Blossom HTTP client, keypair generation, poll loops). The project structure places iOS, Android, relay, and the Rust FFI crate under apps/ (apps/ios/, apps/android/, apps/relay/, apps/olas/). The app uses NMP's push model (nmp_app_set_update_callback / nmp_app_register_event_observer) for events — no polling. The nmp-app-olas Rust crate is a thin registration and kind-20 interest layer only — it calls nmp-defaults::register_defaults() and nmp-blossom::register_actions(), and provides helpers for photo feed, follow pack, and Blossom upload input; it does not reimplement NMP capabilities.

<!-- citations: [^2aff7-51] [^2aff7-72] [^2aff7-83] [^2aff7-92] [^2aff7-98] -->
## NMP Initialization

nmp_app_consume_all_builtin_projections must be called before nmp_app_start or the app will crash with a debug_assert failure.

<!-- citations: [^2aff7-93] [^2aff7-99] -->
