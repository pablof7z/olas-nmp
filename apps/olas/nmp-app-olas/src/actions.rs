use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use nmp_nip68::build::PicturePost;
use nmp_nip68::imeta::ImageMeta;

use crate::location::is_valid_geohash4;

/// Returns the JSON string for the Blossom upload action input.
#[no_mangle]
pub extern "C" fn olas_blossom_upload_input_json(
    file_path: *const c_char,
    mime_type: *const c_char,
    server_url: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(path) = opt_cstr_owned(file_path) else {
            return std::ptr::null_mut();
        };
        let content_type = opt_cstr_owned(mime_type);
        let server =
            opt_cstr_owned(server_url).unwrap_or_else(|| "https://blossom.primal.net".to_string());
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
        json_value_to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

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

/// Build the `nmp.publish` action input for a NIP-68 picture post from a
/// finished Blossom upload. Uses `nmp_nip68::PicturePostBuilder` to assemble
/// the canonical NIP-68 tag set. Event signing, timestamp, and routing stay
/// in the NMP publish action.
#[no_mangle]
pub extern "C" fn olas_picture_post_publish_json(
    blossom_result_json: *const c_char,
    caption: *const c_char,
    alt: *const c_char,
    dim: *const c_char,
    geohash: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(descriptor_str) = opt_cstr_owned(blossom_result_json) else {
            return std::ptr::null_mut();
        };
        let descriptor: serde_json::Value = match serde_json::from_str(&descriptor_str) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };
        let url = match descriptor.get("url").and_then(|v| v.as_str()).filter(|s| !s.is_empty()) {
            Some(u) => u.to_string(),
            None => return std::ptr::null_mut(),
        };
        let sha256 = match descriptor.get("sha256").and_then(|v| v.as_str()).filter(|s| !s.is_empty()) {
            Some(h) => h.to_string(),
            None => return std::ptr::null_mut(),
        };

        let mut image = ImageMeta::new(url).sha256(sha256);
        if let Some(mime) = descriptor.get("type").and_then(|v| v.as_str()) {
            image = image.mime(mime);
        }
        if let Some(dim_str) = opt_cstr_owned(dim) {
            if let Some((w, h)) = dim_str.split_once('x') {
                if let (Ok(w), Ok(h)) = (w.parse::<u32>(), h.parse::<u32>()) {
                    image = image.dimensions(w, h);
                }
            }
        }
        if let Some(alt_str) = opt_cstr_owned(alt) {
            image = image.alt(alt_str);
        }

        let mut builder = PicturePost::new(image)
            .content(opt_cstr_owned(caption).unwrap_or_default());
        if let Some(gh) = opt_cstr_owned(geohash).filter(|gh| is_valid_geohash4(gh)) {
            builder = builder.geohash(gh);
        }

        let draft = match builder.build() {
            Ok(d) => d,
            Err(_) => return std::ptr::null_mut(),
        };

        json_value_to_cstring(serde_json::json!({
            "PublishRaw": {
                "kind": draft.kind,
                "tags": draft.tags,
                "content": draft.content,
                "target": "Auto"
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
