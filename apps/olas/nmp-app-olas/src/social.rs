// social.rs — Real social proof (P0-E) and discover sections (P0-F).
//
// All ranking/graph/dedup logic lives here in Rust.  Native (Swift/Kotlin)
// only renders the returned JSON blobs.
//
// The WoT runtime is wired once in olas_app_register (lib.rs) via
// `set_wot_runtime`; afterwards every FFI call reads from the shared handle.

use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::{Arc, OnceLock};

use nmp_ffi::NmpApp;
use nmp_wot::WotBootstrapRuntime;

// ── Global WoT runtime handle ────────────────────────────────────────────────

static WOT_RUNTIME: OnceLock<Arc<WotBootstrapRuntime>> = OnceLock::new();

/// Called once from `olas_app_register` to wire the WoT runtime handle.
/// Subsequent calls are silently ignored (OnceLock).
pub fn set_wot_runtime(rt: Arc<WotBootstrapRuntime>) {
    let _ = WOT_RUNTIME.set(rt);
}

fn wot_runtime() -> Option<&'static Arc<WotBootstrapRuntime>> {
    WOT_RUNTIME.get()
}

// ── P0-E: Social proof ───────────────────────────────────────────────────────

/// Query the real social proof for `target_pubkey` from `active_pubkey`'s
/// follow graph.
///
/// Returns a JSON object:
/// ```json
/// {
///   "mutual_followers": ["<hex>", ...],
///   "mutual_count": N,
///   "reason_kind": "followed_by_mutuals" | "new_account"
/// }
/// ```
///
/// `"followed_by_mutuals"` means at least one of the viewer's direct follows
/// also follows the target.  `"new_account"` is the honest fallback when there
/// is genuinely no overlap — NOT a fake claim.
///
/// Returns NULL when `active_pubkey` is empty or the WoT runtime is not yet
/// wired.  Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_social_proof_json(
    _app: *mut NmpApp,
    active_pubkey: *const c_char,
    target_pubkey: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if active_pubkey.is_null() || target_pubkey.is_null() {
            return std::ptr::null_mut();
        }
        let viewer = match unsafe { CStr::from_ptr(active_pubkey) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };
        let target = match unsafe { CStr::from_ptr(target_pubkey) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };

        let mutual: Vec<String> = wot_runtime()
            .and_then(|rt| rt.mutual_follows(viewer, target))
            .unwrap_or_default();

        let count = mutual.len();
        let reason_kind = if count > 0 { "followed_by_mutuals" } else { "new_account" };

        let json = serde_json::json!({
            "mutual_followers": mutual,
            "mutual_count": count,
            "reason_kind": reason_kind,
        });
        match serde_json::to_string(&json) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ── P0-F: Discover sections ──────────────────────────────────────────────────

/// Return ranked discover sections for `active_pubkey`.
///
/// Sections are computed kernel-side from the WoT follow graph:
///
/// 1. **"Popular in your circles"** — second-degree contacts ranked by how many
///    of the viewer's direct follows also follow them.  Only included when the
///    graph has at least one second-degree candidate.
/// 2. **"New to discover"** — honest fallback section when the graph has no
///    second-degree data (e.g. account just created or WoT bootstrap not yet
///    completed).  Contains an empty profiles list so the UI can display an
///    appropriate loading/empty state rather than hiding the section entirely.
///
/// JSON shape:
/// ```json
/// [
///   {
///     "title": "Popular in your circles",
///     "reason": "second_degree",
///     "profiles": [
///       { "pubkey": "<hex>", "mutual_count": N },
///       ...
///     ]
///   }
/// ]
/// ```
///
/// Returns NULL when `active_pubkey` is empty, the WoT runtime is absent, or
/// the lock is poisoned.  Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_discover_sections_json(
    _app: *mut NmpApp,
    active_pubkey: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if active_pubkey.is_null() {
            return std::ptr::null_mut();
        }
        let viewer = match unsafe { CStr::from_ptr(active_pubkey) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };

        // Up to 30 second-degree candidates, ranked by mutual-follow count.
        let candidates: Vec<(String, usize)> = wot_runtime()
            .and_then(|rt| rt.ranked_second_degree_candidates(viewer, 30))
            .unwrap_or_default();

        let sections: serde_json::Value = if candidates.is_empty() {
            // Honest fallback: no graph data yet.
            serde_json::json!([
                {
                    "title": "New to discover",
                    "reason": "graph_empty",
                    "profiles": []
                }
            ])
        } else {
            let profiles: Vec<serde_json::Value> = candidates
                .into_iter()
                .map(|(pubkey, count)| {
                    serde_json::json!({ "pubkey": pubkey, "mutual_count": count })
                })
                .collect();
            serde_json::json!([
                {
                    "title": "Popular in your circles",
                    "reason": "second_degree",
                    "profiles": profiles
                }
            ])
        };

        match serde_json::to_string(&sections) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}
