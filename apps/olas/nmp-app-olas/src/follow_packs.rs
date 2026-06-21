// follow_packs.rs — FFI surface for follow-pack discovery (onboarding step).
//
// Pure decode/rank/observer/state logic lives in follow_packs_model.rs (same dir).
// This file covers: consumer IDs, interest management, 4 exported C functions.
//
// AGENTS.md: files split to keep each under the 500-LOC ceiling.

// Route the model module to follow_packs_model.rs (sibling file in src/).
#[path = "follow_packs_model.rs"]
mod model;
pub(crate) use model::install_pack_observer;

use std::collections::HashSet;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use nmp_ffi::NmpApp;

use model::{
    coord_key, global_state, is_valid_pubkey, FEATURED_CURATOR_PUBKEY, KIND_MEDIA_STARTER_PACK,
    MAX_PREVIEW,
};

// ── Consumer IDs for interest open/close ─────────────────────────────────────

const FEATURED_CONSUMER: &str = "olas.follow_packs.featured";
const NETWORK_CONSUMER: &str = "olas.follow_packs.network";
const PROFILE_CONSUMER: &str = "olas.follow_packs.profiles";

// ── Helpers ───────────────────────────────────────────────────────────────────

fn to_cstring(v: serde_json::Value) -> *mut c_char {
    serde_json::to_string(&v)
        .ok()
        .and_then(|s| CString::new(s).ok())
        .map(CString::into_raw)
        .unwrap_or(std::ptr::null_mut())
}

fn empty_state_json(state: &str) -> *mut c_char {
    to_cstring(serde_json::json!({
        "state": state, "packs": [],
        "selection_summary": { "pack_count": 0, "people_count": 0 }
    }))
}

// ── Interest management ───────────────────────────────────────────────────────

fn manage_interests(app: *mut NmpApp, open: bool) {
    if app.is_null() {
        return;
    }
    let filters: &[(serde_json::Value, &str)] = &[
        (
            serde_json::json!({
                "kinds": [39089u32, 39092u32],
                "authors": [FEATURED_CURATOR_PUBKEY],
                "limit": 20
            }),
            FEATURED_CONSUMER,
        ),
        (
            serde_json::json!({ "kinds": [39089u32, 39092u32], "limit": 80 }),
            NETWORK_CONSUMER,
        ),
    ];
    for (filter, consumer) in filters {
        let Ok(f) = serde_json::to_string(filter) else {
            continue;
        };
        let Ok(fc) = CString::new(f) else { continue };
        let Ok(cc) = CString::new(*consumer) else {
            continue;
        };
        if open {
            nmp_ffi::nmp_app_open_interest(app, fc.as_ptr(), cc.as_ptr(), 1);
        } else {
            nmp_ffi::nmp_app_close_interest(app, fc.as_ptr(), cc.as_ptr(), 1);
        }
    }
}

// ── FFI ───────────────────────────────────────────────────────────────────────

/// Open featured + network follow-pack discovery interests (39089/39092). Idempotent.
#[no_mangle]
pub extern "C" fn olas_open_follow_pack_discovery(app: *mut NmpApp) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        manage_interests(app, true);
    }));
}

/// Close the discovery interests. Call when the user leaves the follow-packs step.
#[no_mangle]
pub extern "C" fn olas_close_follow_pack_discovery(app: *mut NmpApp) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        manage_interests(app, false);
    }));
}

/// Snapshot packs as design §4.2 wire JSON. Free with nmp_free_string.
/// state: "loading" (packs cache empty), "ready" (packs present), "empty_offline" (null app).
#[no_mangle]
pub extern "C" fn olas_follow_packs_snapshot_json(app: *mut NmpApp) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if app.is_null() {
            return empty_state_json("empty_offline");
        }
        let state = global_state();
        let (mut ranked, profiles, to_claim) = {
            let guard = match state.lock() {
                Ok(g) => g,
                Err(_) => return empty_state_json("empty_offline"),
            };
            let ranked: Vec<model::RawPack> = guard.packs.values().cloned().collect();
            let profiles = guard.profiles.clone();
            let preview_pks: HashSet<_> = ranked
                .iter()
                .flat_map(|p| p.members.iter().take(MAX_PREVIEW).cloned())
                .collect();
            let to_claim: Vec<String> = preview_pks.difference(&guard.claimed).cloned().collect();
            (ranked, profiles, to_claim)
        };

        ranked.sort_by(|a, b| {
            let m = |p: &model::RawPack| u8::from(p.kind == KIND_MEDIA_STARTER_PACK);
            let f = |p: &model::RawPack| u8::from(p.author == FEATURED_CURATOR_PUBKEY);
            m(b).cmp(&m(a))
                .then(f(b).cmp(&f(a)))
                .then(b.members.len().cmp(&a.members.len()))
                .then(a.title.cmp(&b.title))
                .then(a.d_tag.cmp(&b.d_tag))
        });

        let state_str = if ranked.is_empty() {
            "loading"
        } else {
            "ready"
        };

        let mut default_count = 0usize;
        let packs_json: Vec<serde_json::Value> = ranked
            .iter()
            .map(|pack| {
                let featured = pack.author == FEATURED_CURATOR_PUBKEY;
                let kind_group = if pack.kind == KIND_MEDIA_STARTER_PACK {
                    "media"
                } else {
                    "general"
                };
                let is_default =
                    featured && pack.kind == KIND_MEDIA_STARTER_PACK && default_count < 2 && {
                        default_count += 1;
                        true
                    };
                let avatars: Vec<serde_json::Value> = pack
                    .members
                    .iter()
                    .take(MAX_PREVIEW)
                    .filter_map(|pk| {
                        profiles.get(pk).and_then(|c| {
                            (c.image_url.is_some() || c.display_name.is_some()).then(|| {
                                serde_json::json!({
                                    "image_url": c.image_url,
                                    "display_name": c.display_name,
                                })
                            })
                        })
                    })
                    .collect();
                serde_json::json!({
                    "id": coord_key(pack.kind, &pack.author, &pack.d_tag),
                    "kind_group": kind_group, "featured": featured,
                    "title": pack.title, "cover_image_url": pack.cover_image_url,
                    "description": pack.description, "member_count": pack.members.len(),
                    "preview_avatars": avatars,
                    "social_proof": { "names": [], "extra_count": 0 },
                    "default_selected": is_default,
                })
            })
            .collect();

        // Kick off profile claims for pack preview members not yet requested.
        if !to_claim.is_empty() {
            if let Ok(mut guard) = state.lock() {
                for pk in &to_claim {
                    guard.claimed.insert(pk.clone());
                }
            }
            if let Ok(cc) = CString::new(PROFILE_CONSUMER) {
                for pk in &to_claim {
                    if let Ok(pkc) = CString::new(pk.as_str()) {
                        nmp_ffi::nmp_app_claim_profile(app, pkc.as_ptr(), cc.as_ptr(), 0, 0);
                    }
                }
            }
        }

        to_cstring(serde_json::json!({
            "state": state_str,
            "packs": packs_json,
            "selection_summary": { "pack_count": 0, "people_count": 0 },
        }))
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Expand selected packs, union p-tags, remove dupes + self, dispatch ONE nmp.follow_many.
/// ids_json: JSON array of opaque coord strings from the snapshot.
/// Returns {"follow_count": N, "feed_default": "following"|"network"}.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_apply_selected_follow_packs(
    app: *mut NmpApp,
    ids_json: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if app.is_null() || ids_json.is_null() {
            return std::ptr::null_mut();
        }
        let raw = match unsafe { CStr::from_ptr(ids_json) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };
        let selected: Vec<String> = match serde_json::from_str(raw) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };
        let self_pk: String = unsafe { &*app }
            .active_account_handle()
            .lock()
            .ok()
            .and_then(|g| g.clone())
            .unwrap_or_default();
        let state = global_state();
        let guard = state.lock().unwrap_or_else(|e| e.into_inner());
        let mut seen = HashSet::new();
        let pubkeys: Vec<String> = selected
            .iter()
            .filter_map(|id| guard.packs.get(id))
            .flat_map(|p| p.members.iter().cloned())
            .filter(|pk| pk != &self_pk && is_valid_pubkey(pk) && seen.insert(pk.clone()))
            .collect();
        drop(guard);

        let follow_count = pubkeys.len();
        if !pubkeys.is_empty() {
            let action = match serde_json::to_string(&serde_json::json!({ "pubkeys": pubkeys })) {
                Ok(s) => s,
                Err(_) => return std::ptr::null_mut(),
            };
            let Ok(ns) = CString::new("nmp.follow_many") else {
                return std::ptr::null_mut();
            };
            let Ok(ac) = CString::new(action) else {
                return std::ptr::null_mut();
            };
            let raw_ret = nmp_ffi::nmp_app_dispatch_action(app, ns.as_ptr(), ac.as_ptr());
            if !raw_ret.is_null() {
                nmp_ffi::nmp_free_string(raw_ret);
            }
        }

        let feed_default = if follow_count >= 15 {
            "following"
        } else {
            "network"
        };
        to_cstring(
            serde_json::json!({ "follow_count": follow_count, "feed_default": feed_default }),
        )
    }));
    result.unwrap_or(std::ptr::null_mut())
}
