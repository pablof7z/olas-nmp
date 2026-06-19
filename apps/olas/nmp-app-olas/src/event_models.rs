use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use nmp_core::substrate::KernelEvent;
use serde::Serialize;

use crate::{zaps, NmpApp};

const DEFAULT_RELAYS: &[DefaultRelay] = &[
    DefaultRelay {
        id: "damus",
        name: "Damus",
        icon_host: "damus.io",
        url: "wss://relay.damus.io",
        role: "both",
        connected: true,
    },
    DefaultRelay {
        id: "nos",
        name: "Nos",
        icon_host: "nos.lol",
        url: "wss://nos.lol",
        role: "both",
        connected: true,
    },
    DefaultRelay {
        id: "primal",
        name: "Primal",
        icon_host: "primal.net",
        url: "wss://relay.primal.net",
        role: "both",
        connected: false,
    },
    DefaultRelay {
        id: "purplepages",
        name: "Purple Pages",
        icon_host: "purplepag.es",
        url: "wss://purplepag.es",
        role: "indexer",
        connected: true,
    },
];

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DefaultRelay {
    id: &'static str,
    name: &'static str,
    icon_host: &'static str,
    url: &'static str,
    role: &'static str,
    connected: bool,
}

#[no_mangle]
pub extern "C" fn olas_add_default_relays(app: *mut NmpApp) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        for relay in DEFAULT_RELAYS {
            let Ok(url) = CString::new(relay.url) else {
                continue;
            };
            let Ok(role) = CString::new(relay.role) else {
                continue;
            };
            nmp_ffi::nmp_app_add_relay(app, url.as_ptr(), role.as_ptr());
        }
    }));
}

#[no_mangle]
pub extern "C" fn olas_default_relays_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Ok(json) = serde_json::to_string(DEFAULT_RELAYS) else {
            return std::ptr::null_mut();
        };
        to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "C" fn olas_profile_json(event_json: *const c_char) -> *mut c_char {
    event_json_to_cstring(event_json, profile_json)
}

#[no_mangle]
pub extern "C" fn olas_notification_json(event_json: *const c_char) -> *mut c_char {
    event_json_to_cstring(event_json, notification_json)
}

#[no_mangle]
pub extern "C" fn olas_contact_list_pubkeys_json(
    event_json: *const c_char,
    active_pubkey: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(json) = cstr_trimmed(event_json) else {
            return std::ptr::null_mut();
        };
        let Some(active_pubkey) = cstr_trimmed(active_pubkey) else {
            return std::ptr::null_mut();
        };
        let Ok(event) = serde_json::from_str::<KernelEvent>(&json) else {
            return std::ptr::null_mut();
        };
        if event.kind != 3 || event.author != active_pubkey {
            return std::ptr::null_mut();
        }
        let pubkeys: Vec<&str> = event
            .tags
            .iter()
            .filter(|tag| tag.first().is_some_and(|name| name == "p"))
            .filter_map(|tag| tag.get(1).map(String::as_str))
            .collect();
        let Ok(json) = serde_json::to_string(&pubkeys) else {
            return std::ptr::null_mut();
        };
        to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

fn event_json_to_cstring(
    event_json: *const c_char,
    convert: fn(&KernelEvent) -> Option<String>,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let Some(json) = cstr_trimmed(event_json) else {
            return std::ptr::null_mut();
        };
        let Ok(event) = serde_json::from_str::<KernelEvent>(&json) else {
            return std::ptr::null_mut();
        };
        let Some(json) = convert(&event) else {
            return std::ptr::null_mut();
        };
        to_cstring(json)
    });
    result.unwrap_or(std::ptr::null_mut())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OlasProfile {
    pubkey: String,
    name: Option<String>,
    display_name: Option<String>,
    about: Option<String>,
    picture: Option<String>,
    banner: Option<String>,
    nip05: Option<String>,
    lud16: Option<String>,
}

fn profile_json(event: &KernelEvent) -> Option<String> {
    if event.kind != 0 {
        return None;
    }
    let content: serde_json::Value = serde_json::from_str(&event.content).ok()?;
    let profile = OlasProfile {
        pubkey: event.author.clone(),
        name: string_field(&content, "name"),
        display_name: string_field(&content, "display_name"),
        about: string_field(&content, "about"),
        picture: string_field(&content, "picture"),
        banner: string_field(&content, "banner"),
        nip05: string_field(&content, "nip05"),
        lud16: string_field(&content, "lud16"),
    };
    serde_json::to_string(&profile).ok()
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OlasNotification {
    id: String,
    kind: &'static str,
    actor_pubkey: String,
    post_id: Option<String>,
    created_at: u64,
    zap_sats: Option<u64>,
}

fn notification_json(event: &KernelEvent) -> Option<String> {
    let kind = match event.kind {
        7 => "reaction",
        9735 => "zap",
        1 => "comment",
        3 => "follow",
        _ => return None,
    };
    let post_id = first_tag_value(&event.tags, "e").map(str::to_string);
    let zap_sats = if event.kind == 9735 {
        first_tag_value(&event.tags, "bolt11").and_then(zaps::bolt11_amount_sats)
    } else {
        None
    };
    let notification = OlasNotification {
        id: event.id.clone(),
        kind,
        actor_pubkey: event.author.clone(),
        post_id,
        created_at: event.created_at,
        zap_sats,
    };
    serde_json::to_string(&notification).ok()
}

fn string_field(value: &serde_json::Value, key: &str) -> Option<String> {
    value
        .get(key)
        .and_then(serde_json::Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_string)
}

fn first_tag_value<'a>(tags: &'a [Vec<String>], name: &str) -> Option<&'a str> {
    tags.iter()
        .find(|tag| tag.first().is_some_and(|tag_name| tag_name == name))
        .and_then(|tag| tag.get(1))
        .map(String::as_str)
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

    fn event(kind: u64, content: serde_json::Value, tags: serde_json::Value) -> String {
        serde_json::json!({
            "id": "evt1",
            "author": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "kind": kind,
            "created_at": 42,
            "tags": tags,
            "content": content.to_string()
        })
        .to_string()
    }

    #[test]
    fn profile_json_maps_kind0_content_to_olas_shape() {
        let raw = event(
            0,
            serde_json::json!({"name":"pablo","display_name":"Pablo","picture":"https://x"}),
            serde_json::json!([]),
        );
        let parsed: KernelEvent = serde_json::from_str(&raw).unwrap();
        let json = profile_json(&parsed).unwrap();
        let value: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(
            value["pubkey"],
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        assert_eq!(value["displayName"], "Pablo");
        assert_eq!(value["picture"], "https://x");
    }

    #[test]
    fn notification_json_maps_reaction_target() {
        let raw = event(
            7,
            serde_json::json!("+"),
            serde_json::json!([["e", "post1"]]),
        );
        let parsed: KernelEvent = serde_json::from_str(&raw).unwrap();
        let json = notification_json(&parsed).unwrap();
        let value: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(value["kind"], "reaction");
        assert_eq!(value["postId"], "post1");
    }
}
