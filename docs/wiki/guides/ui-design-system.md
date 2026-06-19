---
title: UI Design System
slug: ui-design-system
topic: product
summary: Product planning must include specific polish specs with dedicated agents specing out the feel of the app exclusively
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

# UI Design System

## Motion, Animation & Haptics

Product planning must include specific polish specs with dedicated agents specing out the feel of the app exclusively. The app must be fully polished, with specific polish product specs dedicated to the feel of the app. The motion design system specifies spring tokens (5), curve tokens (4), a 6-step duration scale in ms, haptic/sound tier tables (iOS UIFeedbackGenerator ↔ Android VibrationEffect), and Reduce Motion fallbacks. Motion design specs exist in docs/motion/ covering spring physics, scroll behavior, image transitions, reactions, publish flow, sound design, navigation, loading, video, polish moments, surfaces, text inputs, empty states, and transition completion. The publish flow includes a filter carousel with per-detent selection haptic (click-wheel style) and a miniplayer-style publish overlay: when a user publishes a picture, the upload fades to a miniplayer-style overlay at the bottom of the screen so the user can continue using the app, with an animated progress bar and status label; the overlay auto-fades 2.5 seconds after 'Posted!' appears. The app is silent by default with exactly 2 opt-in sounds: ShutterSoft (publish) and ZapChime. Empty states use procedural line-art breathing loops (not Lottie) across 6 empty-state types. Five sequential polish agents refined the mockup: visual design system (Titanium frame, refined color tokens, SVG icons), spring physics and animations (press spring, heart-particle burst, iOS push-nav scale, tab bounce, compose rotation), content richness (carousel with scroll-snapping, content-warning blur reveal, blurhash placeholders, pull-to-refresh, live relay indicators), new screens (3-step onboarding, pinch-to-zoom image viewer, settings with tiers, WoT preset picker, post context menu with mute and trust explanation, wallet connect), and final polish (Nostr easter egg on triple-tap wordmark, typing indicator, trust badges, inline caption username, Home Screen sheet, Made with ♡ on Nostr watermark, GPU-accelerated scroll). The mockup is deployed at https://olas-mockup.vercel.app as a high-fidelity interactive iPhone HTML mockup.

Haiku agents must test the app on simulator/emulator by actually using it, and Opus agents must review screenshots for pixel-perfect polish feedback.

<!-- citations: [^2aff7-81] [^2aff7-59] [^2aff7-41] [^2aff7-49] [^2aff7-60] [^2aff7-68] [^2aff7-103] -->
## Typography & Color

The design system uses dark mode tokens: background #0A0A0A (mockup #090909), surface #161616 (#141414 mockup), surface-elevated #1C1C1E, text-primary #F5F5F5, text-secondary #99999E, zap #FBB131, heart #FF375F, with no brand accent color and only SF Pro (iOS) / Google Sans (Android).

<!-- citations: [^2aff7-69] [^2aff7-89] -->

## Per-Relay Views

The app provides per-relay views in magazine style. <!-- [^2aff7-102] -->
