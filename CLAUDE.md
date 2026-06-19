# CLAUDE.md

This file intentionally defers to [`AGENTS.md`](AGENTS.md), which is the canonical contributor guide for this repository. Every rule that applies to agents applies to Claude (and vice versa). `AGENTS.md` is authoritative and this file is a pointer.

## Cold-start reading order

1. [`AGENTS.md`](AGENTS.md) — repository conventions, planning discipline, agent workflow, file-size rules, iOS/Android feature parity.
2. [`docs/plan.md`](docs/plan.md) — temporal release plan, milestones, exit criteria.
3. GitHub Issues — active violations, pending decisions, ordered feature queue. Sort by `priority:*` labels.
4. [`WIP.md`](WIP.md) — work currently on a branch.

## Recurring multi-agent patterns

### `brainstorm` — dual-model ideation with synthesis

Use this pattern when the problem is open-ended, the design space is wide, or you want diverse creative input before committing to a direction.

**How to invoke:** `/brainstorm <topic>`

**What runs:**
1. **Opus agent** (Claude) — deep reasoning, first-principles analysis, architectural implications.
2. **Codex exec** (`codex exec "<topic>"`) — independent exploration from Codex's perspective, drawing on a different model's priors and associations.

Both agents work independently without seeing each other's output. After both complete, synthesize their findings into a single combined document: shared conclusions (raise confidence), divergences (flag for explicit decision), and ideas unique to each (surface for consideration). The synthesis is the deliverable — not either agent's raw output.

**When to use:** Feature design, naming decisions, architectural choices, UX flow exploration, any question where two independent minds beat one.

---

### `PERT` — Plan + Execute + Review + Test

Use this pattern for any non-trivial feature or fix that touches iOS and/or Android code. It enforces the feature-parity rule by making testing a first-class gate.

**How to invoke:** `/PERT <task description>`

**Four phases — each must complete before the next begins:**

1. **Plan** — `codex exec "plan: <task>"` produces a step-by-step implementation plan: files to touch, Rust/Swift/Kotlin split, test approach, iOS/Android parity checklist. Plan is approved by the user before execution starts.

2. **Execute** — Sonnet agent (Claude, worktree-isolated) implements the plan exactly. No scope creep. Opens a PR when done.

3. **Review** — Opus agent (Claude) reads the diff against the plan and AGENTS.md rules. Checks: parity (both platforms touched?), file-size limits (300/500 LOC), no native business logic, no hacks. Produces a structured pass/fail report with specific line citations.

4. **Test** — Haiku agent drives on-device validation:
   - **iOS**: uses the `xcode-build-orchestrator` skill to build for simulator via `xcodebuild`, launches the app, and exercises the changed feature. Screenshots attached to the PR.
   - **Android**: launches the Android emulator via `adb`, installs the debug APK from `just build-android`, and exercises the changed feature.
   - Reports PASS/FAIL per platform. Both must pass before the PR is marked ready for review.

**Invariant:** A PERT cycle that passes Review but fails Test goes back to Execute — not to Plan. A cycle that fails Review does not proceed to Test.
