// nmp-app-olas/src/lib.rs
// Olas-specific registration and interest helpers.
// All business logic (WoT, Blossom, signing, relays) lives in NMP crates.
// Swift/Kotlin call nmp-ffi symbols directly for all standard operations.

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

/// Decode `action_results` from a FlatBuffer snapshot frame (delivered by the
/// update callback) and return them as a JSON array string, or NULL if this
/// frame carries no action results.
///
/// Call this from the update callback (nmp_app_set_update_callback) to pick up
/// async action terminals (nmp.blossom.upload BUD-02 descriptor, nmp.publish
/// verdict) keyed by correlation_id.
///
/// JSON shape: [{correlation_id, status, result?}]
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_decode_snapshot_action_results_json(
    frame: *const u8,
    len: usize,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if frame.is_null() || len == 0 {
            return std::ptr::null_mut();
        }
        let bytes = unsafe { std::slice::from_raw_parts(frame, len) };
        let Ok(projections) = nmp_core::decode_snapshot_typed_projections(bytes) else {
            return std::ptr::null_mut();
        };
        let Some(entry) = projections.iter().find(|p| p.key == "action_results") else {
            return std::ptr::null_mut();
        };
        let Ok(model) = nmp_core::typed_projections::decode_action_results(&entry.payload) else {
            return std::ptr::null_mut();
        };
        if model.results.is_empty() {
            return std::ptr::null_mut();
        }
        let json_rows: Vec<serde_json::Value> = model
            .results
            .into_iter()
            .map(|row| {
                let mut obj = serde_json::json!({
                    "correlation_id": row.correlation_id,
                    "status": row.status,
                });
                if let Some(result_str) = row.result {
                    // result is already a serialised JSON string — parse it into
                    // a Value so it embeds cleanly (not double-encoded).
                    obj["result"] = serde_json::from_str::<serde_json::Value>(&result_str)
                        .unwrap_or(serde_json::Value::Null);
                }
                obj
            })
            .collect();
        let Ok(json) = serde_json::to_string(&json_rows) else {
            return std::ptr::null_mut();
        };
        CString::new(json)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Decode `claimed_profiles` from a FlatBuffer snapshot frame (delivered by the
/// update callback) and return them as a JSON array, or NULL if no claimed
/// profiles are present in this frame.
///
/// Profile data is NOT delivered via the event observer — it arrives here in the
/// snapshot's `claimed_profiles` typed projection on every tick after
/// `nmp_app_claim_profile` is called (with force=1 to bypass the EventStore).
///
/// JSON shape: [{"pubkey":"…","display_name":"…","picture_url":"…","nip05":"…"}]
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_decode_snapshot_claimed_profiles_json(
    frame: *const u8,
    len: usize,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if frame.is_null() || len == 0 {
            return std::ptr::null_mut();
        }
        let bytes = unsafe { std::slice::from_raw_parts(frame, len) };
        let Ok(projections) = nmp_core::decode_snapshot_typed_projections(bytes) else {
            return std::ptr::null_mut();
        };
        let Some(entry) = projections
            .iter()
            .find(|p| p.key == nmp_core::typed_projections::CLAIMED_PROFILES_SCHEMA_ID)
        else {
            return std::ptr::null_mut();
        };
        let Ok(model) = nmp_core::typed_projections::decode_claimed_profiles(&entry.payload) else {
            return std::ptr::null_mut();
        };
        if model.entries.is_empty() {
            return std::ptr::null_mut();
        }
        let json_rows: Vec<serde_json::Value> = model
            .entries
            .iter()
            .map(|(pubkey, card)| {
                let mut obj = serde_json::json!({ "pubkey": pubkey });
                if let Some(dn) = &card.display_name {
                    if !dn.is_empty() {
                        obj["display_name"] = serde_json::Value::String(dn.clone());
                    }
                }
                if let Some(pic) = &card.picture_url {
                    if !pic.is_empty() {
                        obj["picture_url"] = serde_json::Value::String(pic.clone());
                    }
                }
                if !card.nip05.is_empty() {
                    obj["nip05"] = serde_json::Value::String(card.nip05.clone());
                }
                obj["npub"] = serde_json::Value::String(nmp_core::display::to_npub(pubkey));
                obj["npub_short"] =
                    serde_json::Value::String(nmp_core::display::short_npub(pubkey));
                obj
            })
            .collect();
        let Ok(json) = serde_json::to_string(&json_rows) else {
            return std::ptr::null_mut();
        };
        CString::new(json)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Decode `active_account` from a FlatBuffer snapshot frame (delivered by the
/// update callback) and return the active account pubkey as a JSON string, or
/// NULL if no account is active or the projection is absent.
///
/// JSON shape: {"pubkey":"<hex>"} — a single object (NOT an array).
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_decode_snapshot_active_account_json(
    frame: *const u8,
    len: usize,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if frame.is_null() || len == 0 {
            return std::ptr::null_mut();
        }
        let bytes = unsafe { std::slice::from_raw_parts(frame, len) };
        let Ok(projections) = nmp_core::decode_snapshot_typed_projections(bytes) else {
            return std::ptr::null_mut();
        };
        let Some(entry) = projections
            .iter()
            .find(|p| p.key == nmp_core::typed_projections::ACTIVE_ACCOUNT_SCHEMA_ID)
        else {
            return std::ptr::null_mut();
        };
        let Ok(model) = nmp_core::typed_projections::decode_active_account(&entry.payload) else {
            return std::ptr::null_mut();
        };
        let Some(pubkey) = model.pubkey else {
            return std::ptr::null_mut();
        };
        if pubkey.is_empty() {
            return std::ptr::null_mut();
        }
        let json = format!("{{\"pubkey\":\"{}\"}}", pubkey);
        CString::new(json)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

// Re-export nmp-ffi's NmpApp type so this staticlib contains all needed symbols.
pub use nmp_ffi::NmpApp;

mod actions;
mod config_ffi;
mod event_models;
mod location;
mod photo_feed;
mod zaps;

pub use actions::{olas_bookmark_event_action_json, olas_react_action_json, olas_zap_action_json};
pub use config_ffi::{
    olas_compose_steps_json, olas_decode_zap_notification_json, olas_filter_catalog_json,
    olas_media_upload_config_json, olas_onboarding_steps_json, olas_picker_config_json,
    olas_settings_catalog_json,
};
pub use event_models::{
    olas_add_default_relays, olas_contact_list_pubkeys_json, olas_default_relays_json,
    olas_notification_json, olas_profile_json,
};
pub use location::olas_location_geohash4;
pub use photo_feed::olas_filter_photo_post_json;
pub use zaps::olas_bolt11_amount_sats;

// Android JNI shims — gated to android targets only.
#[cfg(target_os = "android")]
mod jni;
#[cfg(target_os = "android")]
mod jni_extras;

// Relay seeding, search feed, and account creation helpers.
mod extras;
pub use extras::{
    olas_close_search_feed, olas_create_account, olas_open_search_feed, olas_seed_default_relays,
};

// New event-decoder, BOLT11, geohash and config FFI helpers.
mod extras_ffi;
pub use extras_ffi::{
    olas_bolt11_amount_msats, olas_build_zap_action_json, olas_compute_geohash,
    olas_decode_kind0_event_json, olas_decode_kind20_event_json,
};

// Mutable app-state: Blossom server URL and feed mode (OnceLock<Mutex<String>>).
mod extras_state;
pub use extras_state::{
    olas_blossom_server_url_get, olas_blossom_server_url_set, olas_feed_mode_get,
    olas_feed_mode_set,
};

/// Register Olas-specific protocol extensions on a freshly constructed NmpApp.
///
/// Call this once, before nmp_app_start, after nmp_app_new.
/// It wires:
///   - nmp-defaults (follow/unfollow/react/zap/WoT bootstrap/routing)
///   - nmp-blossom upload action ("nmp.blossom.upload")
///
/// The caller (Swift or JNI) must also call nmp_app_start after this returns.
#[no_mangle]
pub extern "C" fn olas_app_register(app: *mut NmpApp) {
    // SAFETY: nmp-ffi guarantees NmpApp is Send+Sync and the pointer is valid
    // until nmp_app_free. catch_unwind guards against any panic crossing FFI.
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        // SAFETY: caller guarantees a valid pointer from nmp_app_new, no other
        // exclusive reference aliases it here (same pattern as nmp-app-chirp).
        let app_ref = unsafe { &mut *app };
        let handles = nmp_defaults::register_defaults_with_handles(
            app_ref,
            nmp_defaults::NmpDefaults::default(),
        );
        photo_feed::install_wot_runtime(handles.wot);
        nmp_blossom::register_actions(app_ref);
        event_models::olas_add_default_relays(app);
    }));
}

/// Open a NIP-68 (kind:20) photo feed subscription.
///
/// contact_list_only: 1 = Following feed (contact-list scoped), 0 = Network / global.
/// consumer_id: a unique string tag identifying this subscription (freed by caller).
///
/// Internally calls nmp_app_open_interest with the appropriate kind:20 filter.
/// The NmpApp update callback receives kind:20 events via the standard update frame.
///
/// Network feed events are filtered at decode time by `olas_filter_photo_post_json`,
/// using the shared Rust WoT runtime installed during `olas_app_register`.
#[no_mangle]
pub extern "C" fn olas_open_photo_feed(
    app: *mut NmpApp,
    contact_list_only: u8,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let consumer = if consumer_id.is_null() {
            "olas.photo_feed".to_string()
        } else {
            unsafe { CStr::from_ptr(consumer_id) }
                .to_str()
                .unwrap_or("olas.photo_feed")
                .to_string()
        };

        // NIP-68 picture posts are kind 20.
        // scope: 0 = SCOPE_CONTACT_LIST, 1 = SCOPE_GLOBAL
        let filter_json = if contact_list_only != 0 {
            r#"{"kinds":[20],"limit":50}"#
        } else {
            r#"{"kinds":[20],"limit":100}"#
        };
        let scope: u32 = if contact_list_only != 0 { 0 } else { 1 };

        // SAFETY: nmp_app_open_interest is a #[no_mangle] extern "C" symbol
        // from nmp-ffi; it handles null app gracefully (silent no-op).
        let filter_cstr = match CString::new(filter_json) {
            Ok(s) => s,
            Err(_) => return,
        };
        let consumer_cstr = match CString::new(consumer) {
            Ok(s) => s,
            Err(_) => return,
        };
        nmp_ffi::nmp_app_open_interest(app, filter_cstr.as_ptr(), consumer_cstr.as_ptr(), scope);
    }));
}

/// Returns the JSON string for the Blossom upload action input.
///
/// Caller dispatches via nmp_app_dispatch_action(app, "nmp.blossom.upload", json).
/// This helper constructs the correct UploadInput JSON from the given parameters.
///
/// Parameters match nmp-blossom's UploadInput struct:
///   file_path  — local path to the blob (required).
///   mime_type  — MIME type, e.g. "image/jpeg" (optional; NULL → omit, kernel sniffs).
///   server_url — BUD-02 server base URL (optional; NULL → "https://blossom.primal.net").
///
/// Returned string must be freed via nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_blossom_upload_input_json(
    file_path: *const c_char,
    mime_type: *const c_char,
    server_url: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if file_path.is_null() {
            return std::ptr::null_mut();
        }
        let path = match unsafe { CStr::from_ptr(file_path) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };

        // content_type: None when caller passes null (kernel sniffs from extension).
        let content_type: Option<&str> = if mime_type.is_null() {
            None
        } else {
            unsafe { CStr::from_ptr(mime_type) }.to_str().ok()
        };

        let server = if server_url.is_null() {
            "https://blossom.primal.net"
        } else {
            unsafe { CStr::from_ptr(server_url) }
                .to_str()
                .unwrap_or("https://blossom.primal.net")
        };

        // Build JSON matching nmp-blossom UploadInput:
        //   { "file_path": "...", "content_type": "...", "servers": ["..."] }
        let json = match content_type {
            Some(ct) => serde_json::json!({
                "file_path": path,
                "content_type": ct,
                "servers": [server]
            }),
            None => serde_json::json!({
                "file_path": path,
                "servers": [server]
            }),
        };

        let s = match serde_json::to_string(&json) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        CString::new(s)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Build the `nmp.publish` action input for a NIP-68 (kind:20) picture post from
/// a finished Blossom upload.
///
/// This is the single canonical entry point both platforms use to publish a
/// picture post. It does NOT construct or sign an event — it only assembles the
/// `PublishAction::PublishRaw` envelope (the registered `nmp.publish` verb's wire
/// shape). The kernel's `nmp.publish` action fills `pubkey`, stamps `created_at`
/// (D7 — kernel owns the wall clock), signs with the active account, and routes
/// the kind:20 through the NIP-65 outbox. No event JSON, no `imeta` string, and
/// no signing live in Swift/Kotlin.
///
/// Parameters:
///   blossom_result_json — the JSON from the `nmp.blossom.upload` terminal row
///     (`projections.action_results[cid].result`): a BUD-02 blob descriptor with
///     at least `url` and `sha256` (and optionally `type`). Required.
///   caption — the post body (kind:20 `content`). NULL → empty caption.
///   alt — accessibility alt text for the `imeta` `alt` field. NULL → omitted.
///   dim — pixel dimensions as `"WxH"` for the `imeta` `dim` field (a render fact
///     the capability layer measured). NULL → omitted.
///
/// The `imeta` tag is emitted as a NIP-68 multi-element array
/// (`["imeta", "url …", "x …", "m …", "dim WxH", "alt …"]`), matching the
/// shared `PhotoPostParser` reader on both platforms.
///
/// Returns the `nmp.publish` action JSON to dispatch via
/// `nmp_app_dispatch_action(app, "nmp.publish", json)`, or NULL on malformed
/// input. Returned string must be freed via `nmp_free_string`.
#[no_mangle]
pub extern "C" fn olas_picture_post_publish_json(
    blossom_result_json: *const c_char,
    caption: *const c_char,
    alt: *const c_char,
    dim: *const c_char,
    geohash: *const c_char, // nullable — 4-char NIP-52 "g" tag; NULL → omitted
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if blossom_result_json.is_null() {
            return std::ptr::null_mut();
        }
        let descriptor_str = match unsafe { CStr::from_ptr(blossom_result_json) }.to_str() {
            Ok(s) if !s.trim().is_empty() => s,
            _ => return std::ptr::null_mut(),
        };
        let descriptor: serde_json::Value = match serde_json::from_str(descriptor_str) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };

        // BUD-02 descriptor fields. `url` + `sha256` are required for a usable
        // imeta tag; `type` (MIME) is optional.
        let url = descriptor.get("url").and_then(|v| v.as_str());
        let sha256 = descriptor.get("sha256").and_then(|v| v.as_str());
        let (url, sha256) = match (url, sha256) {
            (Some(u), Some(x)) if !u.is_empty() && !x.is_empty() => (u, x),
            _ => return std::ptr::null_mut(),
        };
        let mime = descriptor.get("type").and_then(|v| v.as_str());

        let opt_str = |p: *const c_char| -> Option<String> {
            if p.is_null() {
                return None;
            }
            unsafe { CStr::from_ptr(p) }
                .to_str()
                .ok()
                .map(str::to_string)
                .filter(|s| !s.is_empty())
        };
        let caption = opt_str(caption).unwrap_or_default();
        let alt = opt_str(alt);
        let dim = opt_str(dim);
        let geohash = opt_str(geohash);

        // NIP-68 imeta as a multi-element tag array (matches PhotoPostParser).
        let mut imeta: Vec<String> = vec![
            "imeta".to_string(),
            format!("url {url}"),
            format!("x {sha256}"),
        ];
        if let Some(m) = mime {
            imeta.push(format!("m {m}"));
        }
        if let Some(d) = &dim {
            imeta.push(format!("dim {d}"));
        }
        if let Some(a) = &alt {
            imeta.push(format!("alt {a}"));
        }

        // NIP-52 geohash location tag — only the 4-char precision the host
        // computes from a coarse CoreLocation fix. EXIF GPS is stripped in the
        // native encoding step; location appears only as a Nostr tag when the
        // user explicitly enables it.
        let extra_tags: Vec<serde_json::Value> = if let Some(gh) = &geohash {
            vec![serde_json::json!(["g", gh])]
        } else {
            vec![]
        };

        // The registered `nmp.publish` verb's `PublishRaw` wire shape. The actor
        // fills pubkey, stamps created_at, signs, and routes via NIP-65 (Auto).
        let mut tags: Vec<serde_json::Value> = vec![serde_json::Value::Array(
            imeta.into_iter().map(serde_json::Value::String).collect(),
        )];
        tags.extend(extra_tags);

        let action = serde_json::json!({
            "PublishRaw": {
                "kind": 20,
                "tags": tags,
                "content": caption,
                "target": "Auto"
            }
        });

        let s = match serde_json::to_string(&action) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        CString::new(s)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}
