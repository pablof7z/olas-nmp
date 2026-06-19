use std::ffi::{CStr, CString};
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn olas_filter_catalog_json() -> *mut c_char {
    json_to_cstring(serde_json::json!([
        {"id": "original", "name": "Original"},
        {"id": "daylight",  "name": "Daylight"},
        {"id": "ember",     "name": "Ember"},
        {"id": "dusk",      "name": "Dusk"},
        {"id": "chrome",    "name": "Chrome"},
        {"id": "fade",      "name": "Fade"},
    ]))
}

#[no_mangle]
pub extern "C" fn olas_media_upload_config_json() -> *mut c_char {
    json_to_cstring(serde_json::json!({
        "max_dimension": 2048,
        "jpeg_quality": 0.92,
        "strip_exif": true,
    }))
}

#[no_mangle]
pub extern "C" fn olas_picker_config_json() -> *mut c_char {
    json_to_cstring(serde_json::json!({
        "max_selection": 1,
        "allowed_types": ["image"],
    }))
}

#[no_mangle]
pub extern "C" fn olas_settings_catalog_json() -> *mut c_char {
    json_to_cstring(serde_json::json!([
        {"id": "content", "label": "Content & Filtering"},
        {"id": "help",    "label": "Help"},
        {"id": "servers", "label": "Servers"},
        {"id": "relays",  "label": "Relays"},
    ]))
}

#[no_mangle]
pub extern "C" fn olas_onboarding_steps_json() -> *mut c_char {
    json_to_cstring(serde_json::json!([
        "welcome",
        "create_account",
        "sign_in",
        "media_server",
        "complete",
    ]))
}

#[no_mangle]
pub extern "C" fn olas_compose_steps_json() -> *mut c_char {
    json_to_cstring(serde_json::json!(["photo_picker", "edit_photo", "caption"]))
}

#[no_mangle]
pub extern "C" fn olas_decode_zap_notification_json(event_json: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        let Some(input) = cstr(event_json) else {
            return std::ptr::null_mut();
        };
        let Ok(event) = serde_json::from_str::<serde_json::Value>(&input) else {
            return std::ptr::null_mut();
        };
        if event["kind"].as_i64() != Some(9735) {
            return std::ptr::null_mut();
        }
        let Some(tags) = event["tags"].as_array() else {
            return std::ptr::null_mut();
        };
        let bolt11 = first_tag_value(tags, "bolt11").unwrap_or_default();
        let amount_sats = bolt11_amount_msats(&bolt11).unwrap_or(0) / 1_000;
        let referenced_event_id = first_tag_value(tags, "e").unwrap_or_default();
        json_to_cstring(serde_json::json!({
            "amount_sats": amount_sats,
            "referenced_event_id": referenced_event_id,
        }))
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn json_to_cstring(value: serde_json::Value) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Ok(s) = serde_json::to_string(&value) else {
            return std::ptr::null_mut();
        };
        CString::new(s)
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

fn first_tag_value(tags: &[serde_json::Value], name: &str) -> Option<String> {
    tags.iter()
        .find(|tag| {
            tag.as_array()
                .and_then(|items| items.first())
                .and_then(|value| value.as_str())
                == Some(name)
        })
        .and_then(|tag| tag.as_array()?.get(1)?.as_str())
        .map(str::to_string)
}

fn bolt11_amount_msats(invoice: &str) -> Option<i64> {
    let normalized = invoice.to_lowercase();
    let separator = normalized.find('1')?;
    let hrp = &normalized[..separator];
    let amount_part = hrp.strip_prefix("ln").and_then(|rest| {
        ["bcrt", "bc", "tb", "sb"]
            .iter()
            .find_map(|currency| rest.strip_prefix(currency))
    })?;
    if amount_part.is_empty() {
        return None;
    }
    let last = amount_part.chars().last()?;
    let digits = if last.is_ascii_alphabetic() {
        &amount_part[..amount_part.len() - last.len_utf8()]
    } else {
        amount_part
    };
    let amount = digits.parse::<i64>().ok()?;
    Some(match last {
        'm' => amount * 100_000_000,
        'u' => amount * 100_000,
        'n' => amount * 100,
        'p' => amount / 10,
        _ => amount * 100_000_000_000,
    })
}

fn cstr(ptr: *const c_char) -> Option<String> {
    if ptr.is_null() {
        return None;
    }
    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .ok()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_string)
}
