---
title: NMP App Overview
slug: nmp-app-overview
topic: nmp-app
summary: The project is an NMP (nostr-multi-platform) app located at ../nostr-multi-platform
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

# NMP App Overview

## Overview

The project is an NMP (nostr-multi-platform) app located at ../nostr-multi-platform. The app targets both iOS and Android, and the two platforms MUST be kept in feature-parity at all times. Every PR that touches one platform must ship the equivalent change on the other platform in the same PR, with no stubs allowed. <!-- [^2aff7-4] -->

## Architecture & Build

The app Rust crate produces a staticlib FFI for iOS and Android. The Cargo.toml workspace pins NMP crates at v0.8.0 rev and includes a commented-in [patch] block for ../nostr-multi-platform, ready to uncomment for local NMP development. The justfile provides commands rust-ios-sim, build-ios-sim, build-android, relay-run, and gen-ios. The relay module is github.com/pablof7z/olas/relay with a minimal HTTP/Nostr scaffold. <!-- [^2aff7-5] -->
