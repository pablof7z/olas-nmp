use std::ffi::CString;
use std::os::raw::c_char;

/// Decode `action_results` from a FlatBuffer snapshot frame (delivered by the
/// update callback) and return them as a JSON array string, or NULL if this
/// frame carries no action results.
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
                    obj["result"] = serde_json::from_str::<serde_json::Value>(&result_str)
                        .unwrap_or(serde_json::Value::Null);
                }
                obj
            })
            .collect();
        let Ok(json) = serde_json::to_string(&json_rows) else {
            return std::ptr::null_mut();
        };
        to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Decode `claimed_profiles` from a FlatBuffer snapshot frame (delivered by the
/// update callback) and return them as a JSON array, or NULL if no claimed
/// profiles are present in this frame.
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
        to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Decode `active_account` from a FlatBuffer snapshot frame and return the
/// active account pubkey as a JSON object string, or NULL if absent.
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
        let Some(pubkey) = model.pubkey.filter(|pk| !pk.is_empty()) else {
            return std::ptr::null_mut();
        };
        to_cstring(format!("{{\"pubkey\":\"{}\"}}", pubkey))
    });
    result.unwrap_or(std::ptr::null_mut())
}

fn to_cstring(value: String) -> *mut c_char {
    CString::new(value)
        .map(CString::into_raw)
        .unwrap_or(std::ptr::null_mut())
}
