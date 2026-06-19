use std::ffi::{CStr, CString};
use std::os::raw::c_char;

/// Build the canonical `nmp.nip25.react` action JSON for reacting to a post.
#[no_mangle]
pub extern "C" fn olas_react_action_json(
    target_event_id: *const c_char,
    target_author_pubkey: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(event_id) = opt_cstr_owned(target_event_id) else {
            return std::ptr::null_mut();
        };
        let mut action = serde_json::json!({
            "target_event_id": event_id,
            "reaction": "+"
        });
        if let Some(author) = opt_cstr_owned(target_author_pubkey) {
            action["target_author_pubkey"] = serde_json::Value::String(author);
        }
        json_value_to_cstring(action)
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Build the canonical `nmp.nip57.zap` action JSON for zapping a post.
#[no_mangle]
pub extern "C" fn olas_zap_action_json(
    recipient_pubkey: *const c_char,
    target_event_id: *const c_char,
    amount_msats: u64,
    comment: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(recipient) = opt_cstr_owned(recipient_pubkey) else {
            return std::ptr::null_mut();
        };
        if amount_msats == 0 {
            return std::ptr::null_mut();
        }
        let mut action = serde_json::json!({
            "recipient_pubkey": recipient,
            "amount_msats": amount_msats,
            "relays": []
        });
        if let Some(event_id) = opt_cstr_owned(target_event_id) {
            action["target_event_id"] = serde_json::Value::String(event_id);
        }
        if let Some(text) = opt_cstr_owned(comment) {
            action["comment"] = serde_json::Value::String(text);
        }
        json_value_to_cstring(action)
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Build the canonical NIP-51 bookmark-list update payload for an event item.
#[no_mangle]
pub extern "C" fn olas_bookmark_event_action_json(
    account_pubkey: *const c_char,
    event_id: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(account) = opt_cstr_owned(account_pubkey) else {
            return std::ptr::null_mut();
        };
        let Some(event_id) = opt_cstr_owned(event_id) else {
            return std::ptr::null_mut();
        };
        json_value_to_cstring(serde_json::json!({
            "account_pubkey": account,
            "item": {
                "type": "event",
                "event_id": event_id
            }
        }))
    });
    result.unwrap_or(std::ptr::null_mut())
}

fn opt_cstr_owned(ptr: *const c_char) -> Option<String> {
    if ptr.is_null() {
        return None;
    }
    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .ok()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_string)
}

fn json_value_to_cstring(value: serde_json::Value) -> *mut c_char {
    let Ok(s) = serde_json::to_string(&value) else {
        return std::ptr::null_mut();
    };
    CString::new(s)
        .map(CString::into_raw)
        .unwrap_or(std::ptr::null_mut())
}
