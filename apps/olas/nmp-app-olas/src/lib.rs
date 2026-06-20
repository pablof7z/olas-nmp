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

// Android JNI shims — gated to android targets only.
#[cfg(target_os = "android")]
mod jni;
#[cfg(target_os = "android")]
mod jni_extras;

mod location;
pub use location::olas_location_geohash4;

mod zaps;
pub use zaps::olas_bolt11_amount_sats;

mod actions;
pub use actions::{
    olas_blossom_upload_input_json, olas_bookmark_event_action_json,
    olas_picture_post_publish_json, olas_react_action_json, olas_zap_action_json,
};

mod event_models;
pub use event_models::{
    olas_contact_list_pubkeys_json, olas_default_relays_json, olas_notification_json,
    olas_profile_json,
};

// Relay seeding, search feed, and account creation helpers.
mod extras;
pub use extras::{
    olas_close_search_feed, olas_create_account, olas_open_search_feed, olas_seed_default_relays,
};

// New event-decoder, BOLT11, geohash and config FFI helpers.
mod extras_ffi;
pub use extras_ffi::{
    olas_bolt11_amount_msats, olas_build_zap_action_json, olas_compose_steps_json,
    olas_compute_geohash, olas_decode_kind0_event_json, olas_decode_kind20_event_json,
    olas_filter_catalog_json, olas_media_upload_config_json, olas_onboarding_steps_json,
    olas_picker_config_json, olas_settings_catalog_json,
};

// Mutable app-state: Blossom server URL and feed mode (OnceLock<Mutex<String>>).
mod extras_state;
pub use extras_state::{
    olas_blossom_server_url_get, olas_blossom_server_url_set, olas_feed_mode_get,
    olas_feed_mode_set, olas_wot_preset_get, olas_wot_preset_set,
};

mod picture_feed;
pub use picture_feed::{olas_decode_snapshot_photo_feed_json, olas_open_photo_feed};

mod photo_feed;
pub use photo_feed::olas_filter_photo_post_json;

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
        picture_feed::install_runtime_handles(app_ref, &handles);
        photo_feed::install_wot_runtime(handles.wot.clone());
        nmp_blossom::register_actions(app_ref);
    }));
}

/// Open a NIP-51 kind:30000 (follow set / starter pack) subscription.
/// Used during onboarding to hydrate follow pack contents.
///
/// pack_addr: the NIP-19 naddr1... or "kind:30000:<pubkey>:<d-tag>" string.
#[no_mangle]
pub extern "C" fn olas_open_follow_pack(app: *mut NmpApp, pack_addr: *const c_char) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if pack_addr.is_null() {
            return;
        }
        let addr = match unsafe { CStr::from_ptr(pack_addr) }.to_str() {
            Ok(s) if !s.is_empty() => s.to_string(),
            _ => return,
        };
        // Open the URI — nmp-ffi handles NIP-19 naddr decoding.
        let addr_cstr = match CString::new(addr) {
            Ok(s) => s,
            Err(_) => return,
        };
        nmp_ffi::nmp_app_open_uri(app, addr_cstr.as_ptr());
    }));
}
