use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::{Arc, Mutex, OnceLock};

use nmp_core::substrate::KernelEvent;
use nmp_wot::WotBootstrapRuntime;

static WOT_RUNTIME: OnceLock<Mutex<Option<Arc<WotBootstrapRuntime>>>> = OnceLock::new();

pub(crate) fn install_wot_runtime(handle: Option<Arc<WotBootstrapRuntime>>) {
    if let Ok(mut slot) = WOT_RUNTIME.get_or_init(|| Mutex::new(None)).lock() {
        *slot = handle;
    }
}

#[cfg(test)]
pub(crate) fn clear_wot_runtime() {
    if let Some(slot) = WOT_RUNTIME.get() {
        if let Ok(mut guard) = slot.lock() {
            *guard = None;
        }
    }
}

#[no_mangle]
pub extern "C" fn olas_filter_photo_post_json(
    event_json: *const c_char,
    contact_list_only: u8,
    wot_preset: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(event_json) = cstr_trimmed(event_json) else {
            return std::ptr::null_mut();
        };
        let preset = cstr_trimmed(wot_preset).unwrap_or_else(|| "balanced".to_string());
        let Some(json) = filter_photo_post_json(&event_json, contact_list_only != 0, &preset)
        else {
            return std::ptr::null_mut();
        };
        to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

fn filter_photo_post_json(
    event_json: &str,
    contact_list_only: bool,
    wot_preset: &str,
) -> Option<String> {
    let event: KernelEvent = serde_json::from_str(event_json).ok()?;
    if event.kind != 20 {
        return None;
    }
    if !contact_list_only && !network_allows(&event.author, wot_preset) {
        return None;
    }
    let record = nmp_nip68::try_from_kernel_event(&event)?;
    crate::picture_feed::photo_post_json_from_record(&record)
}

fn network_allows(candidate: &str, wot_preset: &str) -> bool {
    if wot_preset.eq_ignore_ascii_case("open") {
        return true;
    }
    let runtime = WOT_RUNTIME
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.clone()));
    let Some(runtime) = runtime else {
        return false;
    };
    let Some(viewer) = runtime
        .current_snapshot()
        .and_then(|snapshot| snapshot.active_pubkey)
    else {
        return false;
    };

    let decision = match wot_preset.to_ascii_lowercase().as_str() {
        "close" => runtime.score_with_minimum_score(&viewer, candidate, 20),
        _ => runtime.score_with_minimum_score(&viewer, candidate, 10),
    };
    decision.is_some_and(|decision| !decision.hide)
}

fn cstr_trimmed(ptr: *const c_char) -> Option<String> {
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

fn to_cstring(value: String) -> *mut c_char {
    CString::new(value)
        .map(CString::into_raw)
        .unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn picture_event(tags: serde_json::Value) -> String {
        serde_json::json!({
            "id": "abc",
            "author": "0101010101010101010101010101010101010101010101010101010101010101",
            "kind": 20,
            "created_at": 42,
            "tags": tags,
            "content": "hello #olas"
        })
        .to_string()
    }

    #[test]
    fn following_kind20_event_becomes_photo_post_json() {
        let raw = picture_event(serde_json::json!([
            [
                "imeta",
                "url https://example.com/p.jpg",
                "x abc123",
                "m image/jpeg",
                "dim 800x600",
                "alt beach"
            ],
            ["t", "photography"]
        ]));

        let json = filter_photo_post_json(&raw, true, "balanced").expect("post");
        let value: serde_json::Value = serde_json::from_str(&json).expect("json");

        assert_eq!(value["id"], "abc");
        assert_eq!(
            value["authorPubkey"],
            "0101010101010101010101010101010101010101010101010101010101010101"
        );
        assert_eq!(value["images"][0]["url"], "https://example.com/p.jpg");
        assert_eq!(value["images"][0]["width"], 800);
        assert_eq!(value["hashtags"][0], "photography");
        assert_eq!(value["reactionCount"], 0);
    }

    #[test]
    fn following_feed_rejects_kind20_without_imeta_url() {
        let raw = picture_event(serde_json::json!([["imeta", "x abc123"]]));

        assert!(filter_photo_post_json(&raw, true, "balanced").is_none());
    }

    #[test]
    fn open_network_feed_allows_without_wot_runtime() {
        clear_wot_runtime();
        let raw = picture_event(serde_json::json!([[
            "imeta",
            "url https://example.com/p.jpg",
            "x abc123"
        ]]));

        assert!(filter_photo_post_json(&raw, false, "open").is_some());
    }

    #[test]
    fn balanced_network_feed_rejects_without_wot_runtime() {
        clear_wot_runtime();
        let raw = picture_event(serde_json::json!([[
            "imeta",
            "url https://example.com/p.jpg",
            "x abc123"
        ]]));

        assert!(filter_photo_post_json(&raw, false, "balanced").is_none());
    }
}
