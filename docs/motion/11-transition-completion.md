# 11 — Transition Completion Signals

The **exact, ordered** sequence of visual + haptic + (optional) sound that
confirms success for the app's two heaviest commits — publishing a post and
sending a follow — plus the canonical failure sequence. All timing keys off the
**Rust ack** (relay-accepted / request-confirmed), never off the user's tap.
Tokens from `00-foundations.md`. Cross-refs: `03`, `04`, `04b`, `06`.

---

## 1. Post published — success sequence

Precondition: composer showed a determinate upload ring during flight (`04` §2),
no haptic/sound yet.

| Beat | T (from ack) | Visual | Haptic | Sound |
|---|---|---|---|---|
| 1 | **0ms** | progress ring snaps to full | `Notify(Success)` (iOS `.success` / Android waveform A) | `Sound(ShutterSoft)` **iff** toggle on (`04b`) — sample-aligned with the haptic |
| 2 | 0–200ms | ring morphs to checkmark (stroke trim 0→1, `ease.out`) inside button; button fills green via `spring.bounce` (`1.0→1.12→1.0`) | — | — |
| 3 | 200–260ms | checkmark holds; subtle green glow peaks then settles | — | — |
| 4 | **460ms** | composer dismisses downward (`dur.slow` `spring.gentle`); scrim fades | — | — |
| 5 | on dismiss | new post already at feed top; it plays a **one-time** highlight: background accent-tint 12%→0% over `dur.slow` `ease.standard` (a single gentle "here it is" wash) | — | — |

Notes:
- The success haptic fires **once**, at beat 1 only. Beats 2–5 are silent
  (motion only) — do not stack haptics.
- Reduce Motion: collapse beats 2–3 to a `dur.fast` crossfade to the green check;
  beat 5 highlight becomes a single fade; dismiss becomes a fade. Haptic + sound
  unchanged.
- VoiceOver/TalkBack: at beat 1, native also speaks the Rust-supplied
  announcement "Posted." (`00` §6.)

---

## 2. Follow sent — confirmation sequence

A follow is lighter than a publish: there is **no notification haptic and no
sound** — its confirmation is the satisfying button morph plus one light tick.
(The follow request is optimistic; the visual *is* the confirmation.)

| Beat | T (from commit) | Visual | Haptic | Sound |
|---|---|---|---|---|
| 1 | **0ms** | Rust flips to following (optimistic) | `Impact(Light)` (iOS `.light` / Android `EFFECT_TICK`) | none |
| 2 | 0–80ms | label crossfades "Follow" → check glyph (`ease.standard`) | — | — |
| 3 | 0–220ms | button morphs filled-accent → outlined-quiet, contracts to fit "Following" (`spring.snappy`) | — | — |
| 4 | 120–280ms | checkmark draws (stroke trim 0→1, `ease.out`); settles to "Following ✓" | — | — |

- If the follow request **later fails** at the relay (rare, async): silently
  revert the button to *Follow* via `spring.snappy` and fire `Notify(Warning)`
  (iOS `.warning` / Android waveform B) **once** with a quiet inline "Couldn't
  follow · Retry." No sound. This is the only case where follow produces a
  notification haptic, and it is a *failure*, not the success path.

---

## 3. Canonical failure sequence (publish / upload / zap)

| Beat | T (from failure ack) | Visual | Haptic | Sound |
|---|---|---|---|---|
| 1 | **0ms** | control turns destructive-red, glyph → "!" | `Notify(Error)` (iOS `.error` / Android waveform C) | **none** (`04b` §1.5) |
| 2 | 0–360ms | error shake: 2 cycles, ±6pt, 90ms each, `spring.snappy` (`06` §3) | — | — |
| 3 | after shake | quiet inline affordance "Couldn't <verb> · Retry" appears (rises 6pt + fades, `dur.base`); **does not auto-dismiss** | — | — |

- The error haptic fires **once** at beat 1, concurrent with the shake start.
- Retry tap fires **no** haptic (a fresh attempt); its eventual result runs the
  success (§1) or failure (§3) sequence anew.
- Reduce Motion: skip beat 2's shake; crossfade to the red "!" state over
  `dur.fast`. Haptic unchanged.

---

## 4. Invariants (apply to all completion signals)

1. **One haptic per commit.** Never two generators in one sequence.
2. **Sound only on the two positive heavy commits, only if the toggle is on,**
   only sample-aligned to beat 1. Never on failure, never on follow.
3. **Everything keys off the Rust ack**, so the confirmation is *truthful* — the
   user never feels "success" before the relay actually accepted it. Optimistic
   UI (follow) is the deliberate exception and self-heals on async failure (§2).
4. **Severity matches consequence:** publish/zap → `Notify(Success)`; follow →
   `Impact(Light)`; failures → `Notify(Error)`/`Notify(Warning)`. (`00` §4.2.)
