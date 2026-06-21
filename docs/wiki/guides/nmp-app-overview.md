---
title: NMP App Overview
slug: nmp-app-overview
topic: nmp-app
summary: Olas is an NMP (Nostr Multi-Platform) app located at ../nostr-multi-platform
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

Olas is an NMP (Nostr Multi-Platform) app located at ../nostr-multi-platform. The app targets both iOS and Android, and the two platforms MUST be kept in feature-parity at all times. Every PR that touches one platform must ship the equivalent change on the other platform in the same PR, with no stubs allowed. The app's bundle ID is io.f7z.olas for both iOS and Android.

<!-- citations: [^2aff7-4] [^2aff7-20] [^2aff7-34] -->
## Architecture & Build

The directory structure places iOS, Android, relay, and the Rust crate under apps/: apps/ios/, apps/android/, apps/relay/, apps/olas/. NMP already provides nmp-ffi (full C-ABI lifecycle including nmp_app_create_new_account, nmp_app_signin_nsec, nmp_app_start, nmp_app_set_update_callback push model), nmp-blossom (Blossom upload as NMP action via register_actions()), nmp-wot (WotGraph, TrustDecision, bootstrap via register_defaults()), and nmp-android-ffi (JNI shim wrapping nmp-ffi). The nmp-app-olas crate is a thin registration crate that only adds Olas-specific helpers: olas_app_register (calls nmp-defaults + nmp-blossom), olas_open_photo_feed (kind-20 interest), olas_open_follow_pack (NIP-51 via nmp_app_open_uri), and olas_blossom_upload_input_json (action JSON builder). NMP architecture must be closely followed with no hacks — nmp-ffi already provides the entire C-ABI surface (lifecycle, identity, relay, feed, publish, wallet) and uses a push model via nmp_app_set_update_callback, not polling. iOS and Android projects reside under apps/ios and apps/android respectively, and the relay project resides under apps/relay. Olas consumes NMP crates from the upstream `master` branch via git dependencies in the workspace Cargo.toml, so it follows the active NMP integration line instead of pinning a commit hash. The justfile provides commands rust-ios-sim, build-ios-sim, build-android, relay-run, and gen-ios. The relay module is github.com/pablof7z/olas/relay with a minimal HTTP/Nostr scaffold. An interactive HTML mockup is deployed at https://olas-mockup.vercel.app, designed to look like the real app when opened in a mobile browser, with an Add to Home Screen prompt for iPhone Safari testing. The WIP.md file is gitignored.

<!-- citations: [^2aff7-5] [^2aff7-8] [^2aff7-21] [^2aff7-35] [^2aff7-45] [^2aff7-64] [^2aff7-75] [^2aff7-94] -->
## NMP Dependency Policy

When NMP provides inadequate or incomplete WoT functionality, the team must not hack around it; instead, file a GH issue on the NMP repo, launch a PERT flow on the NMP codebase, land the PR, and then consume it in Olas. Building Olas includes improving NMP itself and finding gaps; gaps must be skeptically validated by Codex to avoid bloating NMP concerns and to protect NMP boundaries from single-app concerns. Known NMP WoT gaps that need GH issues (after Codex validation) include: batch score API, result caching, configurable thresholds, graph introspection API, and persistence across sessions.

<!-- citations: [^2aff7-22] [^2aff7-36] -->
