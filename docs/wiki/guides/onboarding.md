---
title: Onboarding
slug: onboarding
topic: product
summary: "The onboarding flow is a 6-step process: Splash â Welcome â Create account â Follow packs â Media server â All set! Account creation must not expose t"
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

# Onboarding

## Onboarding Flow

The onboarding flow is a 6-step process: Splash → Welcome → Create account → Follow packs → Media server → All set! Account creation must not expose technical key material; users register a username@olas.app address while the keypair is generated silently in the background, and NIP-05 registration is wired later. The app uses NIP-51 kind 30000 follow packs (Categorized People Lists) and media follow packs for seeding the user's initial follows. An interactive HTML mockup is deployed at https://olas-mockup.vercel.app.

<!-- citations: [^2aff7-56] [^2aff7-67] [^2aff7-77] [^2aff7-86] -->
