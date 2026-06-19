# Repository Guidance

This is the authoritative contributor guide for the Olas repository — for agents and humans equally. `CLAUDE.md` defers to this file.

## Agent workflow

- All implementation work must happen in a git worktree owned by the agent doing the work.
- Each agent is responsible for its own branch/worktree lifecycle. Do not edit from the shared root checkout for feature, fix, or refactor work.
- Before starting work, every agent must read `WIP.md` from the project base directory to understand what other agents are currently doing.
- When an agent starts work, it must add an entry to `WIP.md` in the project base directory with a timestamp, a one-line description of the work, and the git worktree path it is using.
- `WIP.md` is gitignored and must NEVER be committed. It lives only in the main checkout directory and is shared across all worktrees.
- When the work is complete, open a pull request before reporting completion. The PR description must include:
  - a short TLDR summary of what changed;
  - a detailed overview of the work performed;
  - validation performed;
  - any subjective decisions made, including tradeoffs or assumptions.
- Do not open completed work as a draft pull request. Use draft PRs only when explicitly asked or when the work is intentionally incomplete.
- After opening the PR, clean up the agent-owned worktree before completing the task.

## iOS / Android feature parity — non-negotiable

**Both platforms must ship every user-facing feature simultaneously.** There is no "iOS first, Android later" or vice versa. This is an absolute rule:

- Any PR that adds, removes, or changes a feature on one platform MUST include the equivalent change on the other platform in the same PR.
- Feature flags, stubs, or "TODO: Android" placeholders are forbidden. If a feature cannot be completed on both platforms in a single PR, the feature must not merge until it is ready on both.
- UI parity does not require pixel-identical screens — native design conventions (iOS HIG vs Material 3) apply — but the underlying capability, data model, and user-reachable functionality must be identical.
- Test coverage requirements apply equally to both platforms. A feature is not validated until both iOS and Android builds exercise it.

## File Size

- Keep hand-authored source and documentation files under 300 lines of code where practical.
- Treat 500 lines of code as a hard ceiling for hand-authored files.
- Split files by cohesive ownership when they approach the soft limit. Prefer feature modules, sibling views, or linked docs over large catch-all files.
- Generated, vendored, lockfile, binary, and benchmark-output artifacts are exempt from the LOC ceiling.

## Architecture: Rust owns all logic; native is rendering + capabilities only

- **No native business logic.** If you would write an `if` statement in Swift, Kotlin, or Go that decides what the app should *do* (not how it should *look* or *relay*), that logic belongs in Rust. Native is rendering plus capability execution. Nothing else.
- Native code (Swift, Kotlin) is allowed to do exactly two things:
  1. **Render** — translate Rust-produced state snapshots into UI.
  2. **Execute capabilities** — call OS APIs (Keychain, camera, push, location) and report raw results back to Rust. Never decide policy; never retry; never cache.
- Everything else — state, business rules, derived data, routing decisions, error recovery, protocol logic — lives in Rust.
- The relay (Go) is a separate process and is exempt from this rule; it owns its own state.

## Effects, replay, and snapshot discipline

- Every external effect is represented as typed data crossing the Rust/native boundary: Rust requests a capability, native reports a raw result, Rust decides the next state.
- New nondeterministic inputs (time, randomness, network, OS callbacks) must enter the actor as explicit actions/events or injected seams. Reducers must remain replayable from message history.
- Never record secrets, raw nsecs, plaintext DMs, or bearer tokens in debug/history surfaces.

## Planning discipline

This repository uses three canonical planning/status surfaces:

| Surface | Role | Update cadence |
|---|---|---|
| `docs/plan.md` | Temporal release plan — milestones, current state, exit criteria. | Only when a milestone changes status. |
| GitHub Issues | Tactical queue — active violations, pending decisions, ordered feature work, follow-ups. | Every PR that touches a queued item. |
| `WIP.md` | Live in-flight tracker — branches currently on a worktree. | When an agent starts work and when work finishes. |

Rules:
- Do not create new top-level planning files. No `PLAN.md`, `TODO.md`, `ROADMAP.md`, `NEXT.md`, or ad-hoc plan files at the repo root.
- Do not duplicate state across files.
- Inline `// TODO:` comments are not a planning system. Promote them to GitHub issues.
- Plans are coordination artifacts. When a plan completes, remove it or collapse it to the smallest live follow-up.

## TEA organization: co-locate by owner, not by role

- Do not create top-level `model/`, `update/`, `view/`, `state/`, or `actions/` buckets.
- Prefer one cohesive module per feature, page, or domain type. Keep state, actions, projection, and tests near that owner.
- The LOC rule still wins. When a cohesive owner approaches the limit, split under the same owner namespace by concrete sub-type, not by recreating global layers.

## No polling — ever

Polling is forbidden at every layer: no `sleep` + check loops, no timer querying state, no spin loops.

Use blocking primitives or event-driven patterns:
- **Rust channels**: block with `recv()` / `recv_timeout()`.
- **iOS**: consume state snapshots pushed by the kernel; use AVFoundation/NWPathMonitor callbacks for OS events.
- **Go relay**: use channel-based concurrency; no busy-wait loops.

## Zero-tolerance on hacks, debt, and fragmentation

- No temporary hacks. A staged fix is allowed only when a GitHub issue labeled `status:staged` documents every stage.
- No fragmentation: every concept has exactly one canonical representation and one code path.
- Every change must be done by the book, seeking the long-term correct architecture.
- "It works" is not an acceptance criterion. "It works and is architecturally correct" is.

## NMP crate boundaries

- **NMP crates** (`../nostr-multi-platform/crates/`) provide reusable Nostr infrastructure. Do not duplicate their logic here.
- **App Rust crates** (`apps/olas/`) hold Olas-specific Rust logic that would not generalize to other Nostr apps.
- Keep the two layers cleanly separated. Olas app crates depend on NMP crates; NMP crates never depend on Olas crates.
