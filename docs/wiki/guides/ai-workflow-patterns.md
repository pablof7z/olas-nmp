---
title: AI Workflow Patterns
slug: ai-workflow-patterns
topic: code-standards
summary: "CLAUDE.md defines two recurring multi-agent patterns: 'brainstorm' (parallel Opus agent and Codex exec independently, then synthesized into one output) and 'PER"
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

# AI Workflow Patterns

## AI Workflow Patterns

CLAUDE.md defines two recurring multi-agent patterns: 'brainstorm' (parallel Opus agent and Codex exec independently, then synthesized into one output) and 'PERT' (Codex exec plans, Sonnet agent executes, Opus agent reviews screenshots for pixel-perfect polish feedback, and Haiku agent tests on-device via iOS simulator through the xcode-build-orchestrator skill or Android emulator by actually using the app). In the PERT pattern, if a review step fails, the flow loops back to the execute step, not the plan step.

<!-- citations: [^2aff7-6] [^2aff7-16] [^2aff7-33] [^2aff7-50] [^2aff7-61] [^2aff7-71] [^2aff7-91] -->
