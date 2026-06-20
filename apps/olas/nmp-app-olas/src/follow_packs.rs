// follow_packs.rs — P0-A: follow-pack discovery and bulk-apply FFI.
//
// Architecture:
//   * Discovery: open a kind:30000 (NIP-51 follow set) interest so events
//     arrive via the standard kernel event observer. The native side decodes
//     kind:30000 events (they have `p` tags = member pubkeys and a `d` tag =
//     pack name/id) and renders the pack cards.
//   * Apply: native collects the resolved pubkeys from the packs the user
//     selected (it already has them from the rendered kind:30000 events),
//     deduplicates them, excludes the active account's own pubkey, and passes
//     the final list to `olas_apply_follow_pack_pubkeys`. Rust dispatches one
//     `nmp.follow` per pubkey through the action bus. The NMP kernel merges
//     each follow into the active kind:3 contact list and re-publishes.
//   * Return: `{"correlation_id":"<last_cid>","follow_count":N,
//              "feed_default":"following|network"}`.
//     feed_default is "following" when N >= 15, "network" otherwise.
//
// AGENTS.md compliance:
//   * No business logic in Swift/Kotlin — pubkey dedup + self-exclusion + feed
//     default decision all live here in Rust.
//   * No native follow loop — Rust owns the dispatch loop.

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use nmp_ffi::NmpApp;

// Known Olas-curated follow-pack authors (kind:30000 events from these pubkeys
// are the "Olas starter packs"). Additional well-known pack curators can be
// added here without native changes.
const PACK_AUTHOR_PUBKEYS: &[&str] = &[
    // pablof7z (olas.app author, publishes the canonical starter packs)
    "fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52",
];

/// Open a NIP-51 kind:30000 (follow set / starter pack) discovery interest.
///
/// Subscribes to kind:30000 events from the canonical Olas pack authors
/// (global scope). Events arrive via the kernel event observer as standard
/// Nostr events with `p` tags carrying member pubkeys and a `d` tag carrying
/// the pack identifier. Close with `olas_close_follow_pack_discovery` using
/// the same `consumer_id`.
///
/// consumer_id: caller-owned string identifying this subscription.
#[no_mangle]
pub extern "C" fn olas_open_follow_pack_discovery(
    app: *mut NmpApp,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        let consumer = consumer_id_str(consumer_id, "olas.follow_packs");
        let authors: Vec<&str> = PACK_AUTHOR_PUBKEYS.to_vec();
        let filter = match serde_json::to_string(&serde_json::json!({
            "kinds": [30000],
            "authors": authors,
            "limit": 20,
        })) {
            Ok(s) => s,
            Err(_) => return,
        };
        let Ok(filter_c) = CString::new(filter) else { return };
        let Ok(consumer_c) = CString::new(consumer) else { return };
        // scope 1 = global
        nmp_ffi::nmp_app_open_interest(app, filter_c.as_ptr(), consumer_c.as_ptr(), 1);
    }));
}

/// Close the follow-pack discovery interest opened with
/// `olas_open_follow_pack_discovery`. Must be called with the same
/// `consumer_id` as the matching open call.
#[no_mangle]
pub extern "C" fn olas_close_follow_pack_discovery(
    app: *mut NmpApp,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        let consumer = consumer_id_str(consumer_id, "olas.follow_packs");
        let authors: Vec<&str> = PACK_AUTHOR_PUBKEYS.to_vec();
        let filter = match serde_json::to_string(&serde_json::json!({
            "kinds": [30000],
            "authors": authors,
            "limit": 20
        })) {
            Ok(s) => s,
            Err(_) => return,
        };
        let Ok(filter_c) = CString::new(filter) else { return };
        let Ok(consumer_c) = CString::new(consumer) else { return };
        nmp_ffi::nmp_app_close_interest(app, filter_c.as_ptr(), consumer_c.as_ptr(), 1);
    }));
}

/// Decode a kind:30000 Nostr event JSON into a FollowPack descriptor for the
/// native UI to render. Returns NULL when the event is not a valid / useful
/// kind:30000. Returned string must be freed with nmp_free_string.
///
/// Input: raw event JSON from the kernel event observer.
/// Output: `{"id":"<d-tag>","name":"<title-tag or d-tag>","description":"<about-tag>",
///           "accent_color":"<color-tag or default>","pubkeys":["<hex>",...],"count":N}`
///
/// The native side renders this struct into a FollowPackCard and stores the
/// pubkeys for the apply step.
#[no_mangle]
pub extern "C" fn olas_decode_follow_pack_event_json(
    event_json: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if event_json.is_null() {
            return std::ptr::null_mut();
        }
        let raw = match unsafe { CStr::from_ptr(event_json) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };
        let event: serde_json::Value = match serde_json::from_str(raw) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };
        // Only handle kind:30000
        if event.get("kind").and_then(|k| k.as_u64()) != Some(30000) {
            return std::ptr::null_mut();
        }
        let tags = match event.get("tags").and_then(|t| t.as_array()) {
            Some(t) => t,
            None => return std::ptr::null_mut(),
        };

        // Extract tag values by key.
        let get_tag = |key: &str| -> Option<String> {
            tags.iter()
                .find_map(|t| {
                    let arr = t.as_array()?;
                    if arr.first()?.as_str()? == key {
                        arr.get(1)?.as_str().map(str::to_string)
                    } else {
                        None
                    }
                })
        };
        let d_tag = match get_tag("d") {
            Some(d) if !d.is_empty() => d,
            _ => return std::ptr::null_mut(),
        };
        let title = get_tag("title").unwrap_or_else(|| d_tag.clone());
        let description = get_tag("about").or_else(|| get_tag("description")).unwrap_or_default();
        let accent_color = get_tag("color").unwrap_or_else(|| "#8B5CF6".to_string());

        // Collect `p` tag pubkeys — these are the pack members.
        let pubkeys: Vec<String> = tags
            .iter()
            .filter_map(|t| {
                let arr = t.as_array()?;
                if arr.first()?.as_str()? == "p" {
                    arr.get(1)?.as_str().map(str::to_string)
                } else {
                    None
                }
            })
            .filter(|pk| pk.len() == 64) // hex pubkey length check
            .collect();

        if pubkeys.is_empty() {
            return std::ptr::null_mut();
        }

        let count = pubkeys.len();
        let out = serde_json::json!({
            "id": d_tag,
            "name": title,
            "description": description,
            "accent_color": accent_color,
            "pubkeys": pubkeys,
            "count": count,
        });
        let Ok(json) = serde_json::to_string(&out) else {
            return std::ptr::null_mut();
        };
        CString::new(json)
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut())
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Apply the selected follow packs by dispatching one `nmp.follow` action per
/// unique pubkey in `pubkeys_json`.
///
/// pubkeys_json: JSON array of hex pubkey strings — the union of all `p` tags
///   from the selected kind:30000 events, deduplicated and with the active
///   account's own pubkey already removed by the caller.
/// active_pubkey: the active account's hex pubkey (used to guard against
///   self-follow in case the caller missed any). Pass empty string if unknown.
///
/// Returns: `{"follow_count":N,"feed_default":"following|network"}` where
///   feed_default is "following" when N >= 15, "network" otherwise.
/// Returns NULL on decode error or empty input.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_apply_follow_pack_pubkeys(
    app: *mut NmpApp,
    pubkeys_json: *const c_char,
    active_pubkey: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if app.is_null() || pubkeys_json.is_null() {
            return std::ptr::null_mut();
        }
        let raw_json = match unsafe { CStr::from_ptr(pubkeys_json) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };
        let active_pk: String = if active_pubkey.is_null() {
            String::new()
        } else {
            unsafe { CStr::from_ptr(active_pubkey) }
                .to_str()
                .unwrap_or("")
                .to_string()
        };

        let pubkeys: Vec<String> = match serde_json::from_str(raw_json) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };

        // Dedup while preserving order; exclude self.
        let mut seen = std::collections::HashSet::new();
        let deduped: Vec<String> = pubkeys
            .into_iter()
            .filter(|pk| !pk.is_empty() && pk.len() == 64 && pk != &active_pk)
            .filter(|pk| seen.insert(pk.clone()))
            .collect();

        if deduped.is_empty() {
            return std::ptr::null_mut();
        }

        // Dispatch one nmp.follow per pubkey. The NMP kernel owns the follow-list
        // write path and will merge each into the active kind:3 contact list.
        let follow_ns = match CString::new("nmp.follow") {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let count = deduped.len();
        for pk in &deduped {
            let action_json = format!("{{\"pubkey\":\"{}\"}}", pk);
            let Ok(json_c) = CString::new(action_json) else { continue };
            let raw = nmp_ffi::nmp_app_dispatch_action(app, follow_ns.as_ptr(), json_c.as_ptr());
            if !raw.is_null() {
                nmp_ffi::nmp_free_string(raw);
            }
        }

        let feed_default = if count >= 15 { "following" } else { "network" };
        let out = serde_json::json!({
            "follow_count": count,
            "feed_default": feed_default,
        });
        let Ok(json) = serde_json::to_string(&out) else {
            return std::ptr::null_mut();
        };
        CString::new(json)
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut())
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn consumer_id_str(ptr: *const c_char, default: &str) -> String {
    if ptr.is_null() {
        return default.to_string();
    }
    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .unwrap_or(default)
        .to_string()
}
