# Olas — WoT Integration Spec

## What nmp-wot provides today

`nmp-wot` is an event-driven, in-memory trust scoring library built into the NMP kernel. It ingests kind-3 (follow lists) and kind-10000 (mute lists) via `KernelEventObserver` and exposes a single query API:

```rust
fn score(viewer: &str, candidate: &str) -> TrustDecision
// TrustDecision { score: i32, hide: bool, reason: &'static str }
```

**Score algorithm:**
| Relationship | Points |
|---|---|
| Self (viewer == candidate) | +1000 |
| Direct follow | +100 |
| Second-degree follow (per connection) | +10 |
| Self-muted | -1000 (hide=true always) |
| Muted by someone viewer follows (per mutor) | -25 |
| Auto-hide threshold | score ≤ -50 → hide=true |

`reason` values: `"self"`, `"direct-follow"`, `"second-degree"`, `"muted-by-self"`, `"muted-by-followed"`, `"unknown"`.

Bootstrap: `WotBootstrapRuntime` auto-fetches the active user's second-degree graph on startup via a `OneShot` interest across all read relays.

## How Olas uses it

### Feed filtering

```rust
// Pseudo-code in nmp-app-olas
let visible = events
    .iter()
    .filter(|e| !wot.score(&viewer_pubkey, &e.pubkey).hide)
    .collect();
```

For `Network` feed: include events where `hide == false`.
For `Following` feed: include all direct-follow events regardless of WoT score (they always score ≥ +100, so this is implicit, but the code should not call `score()` unnecessarily for followed accounts).

### WoT preset → threshold mapping

| User-facing preset | hide threshold | Effective behavior |
|---|---|---|
| Close | score < +10 | Only direct follows + very strong 2nd-degree |
| Balanced (default) | use `wot.score().hide` | NMP's default auto-hide at ≤ -50 |
| Open | no filtering | All events pass regardless of score |

In Power Mode: expose a raw slider mapping 0–100 to a configurable `min_score` threshold.

### Trust transparency UI

`TrustDecision.reason` maps to human-facing copy:
- `"direct-follow"` → "You follow this person"
- `"second-degree"` → "Followed by [name] + N others you follow"
- `"muted-by-self"` → never shown (hidden, no UI)
- `"unknown"` → "Outside your network"
- `"muted-by-followed"` → hidden with "Outside your network" label

### Notification filtering

Notifications use a stricter preset than the feed by default — equivalent to "Close" (only direct follows and very strong second-degree). Configurable separately from feed WoT.

## NMP gaps for the Olas photo feed

These are real gaps in `nmp-wot` for the feed use-case. Each must be **validated by Codex before filing an NMP issue** — the test is whether the fix belongs in NMP (general Nostr building block) or in Olas's app crate (single-app concern).

### Gap A — No batch scoring API
`score()` is called once per event author. For a feed of 200 events with 150 unique authors, this is 150 individual graph traversals. For large follow graphs this may be slow.

**Candidate NMP fix:** `fn batch_score(viewer: &str, candidates: &[&str]) -> Vec<TrustDecision>` with memoization keyed by `(viewer, session)`.

**Validation question for Codex:** Is the traversal actually O(follows) per call, making 150 calls meaningfully expensive on a graph of 5k follows? Benchmark first before filing.

### Gap B — No configurable thresholds
Auto-hide is hardcoded at `score ≤ -50`. Olas needs the "Close" preset to use a higher threshold (+10).

**Candidate NMP fix:** `fn score_with_threshold(viewer: &str, candidate: &str, min_score: i32) -> TrustDecision`.

**Validation question:** Is per-call threshold a general NMP concern or Olas-specific? Probably general — file if Codex agrees.

### Gap C — No graph introspection for "followed by" UI
The "Followed by Ana + 3 others" trust line requires knowing *which* of the viewer's follows also follow the candidate — not just the count.

**Candidate NMP fix:** `fn mutual_follows(viewer: &str, candidate: &str) -> Vec<String>` returning pubkeys.

**Validation question:** Other apps (social explorers, profile pages) would also want this. Likely a general building block — file.

### Gap D — No persistence across app launches
`WotGraph` is in-memory only. On cold start, the graph is empty until bootstrap completes (can be seconds to minutes on slow connections). During this window the Network feed is empty or incorrectly unfiltered.

**Candidate NMP fix:** Serialize `WotGraph` to the lmdb store on background tick; restore on boot before first render.

**Validation question:** This is a substrate-level concern (event store + graph cache) that every NMP app would benefit from. Strong candidate for NMP — validate with Codex.

### Gap E — No sparse-graph graceful degradation
Unknown authors (`reason == "unknown"`) score 0 and do not hide. In a fresh Network feed, a user following 0 people will see *everything* on the relay — effectively no filtering. This defeats the "safe by default" promise.

**Olas-side mitigation (app crate, not NMP):** When the viewer's follow count is 0, fall back to starter-pack seed pubkeys as trusted anchors. This is single-app logic.

**NMP gap question:** Should NMP expose a "no-graph" signal so app crates can detect and handle it? Or is the `follow_author_count == 0` check sufficient? Validate with Codex before filing.

## Process for filing NMP gaps

Per AGENTS.md:
1. **Validate gap with `codex exec`** — confirm it's a real performance or correctness issue and that the fix generalizes beyond Olas.
2. If validated: **file a GitHub issue on `pablof7z/nostr-multi-platform`** with label `area:nmp-wot`, include the Olas use-case as motivation.
3. **Launch a PERT flow on the NMP codebase** to implement and land the fix.
4. **Bump the NMP pin** in `Cargo.toml` to the new rev.
5. **Update the Olas app crate** to consume the new API.

Do not implement workarounds in the Olas app crate for gaps that belong in NMP.
