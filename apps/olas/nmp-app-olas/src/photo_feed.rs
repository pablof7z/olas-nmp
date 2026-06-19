use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::{Arc, Mutex, OnceLock};

use nmp_core::substrate::KernelEvent;
use nmp_wot::WotBootstrapRuntime;
use serde::Serialize;

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
    filter_photo_post_json_with_runtime(event_json, contact_list_only, wot_preset, wot_runtime())
}

fn filter_photo_post_json_with_runtime(
    event_json: &str,
    contact_list_only: bool,
    wot_preset: &str,
    runtime: Option<Arc<WotBootstrapRuntime>>,
) -> Option<String> {
    let event: KernelEvent = serde_json::from_str(event_json).ok()?;
    if event.kind != 20 {
        return None;
    }
    if !contact_list_only && !network_allows(&event.author, wot_preset, runtime) {
        return None;
    }
    let post = PhotoPost::from_event(&event)?;
    serde_json::to_string(&post).ok()
}

fn wot_runtime() -> Option<Arc<WotBootstrapRuntime>> {
    WOT_RUNTIME
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.clone()))
}

fn network_allows(
    candidate: &str,
    wot_preset: &str,
    runtime: Option<Arc<WotBootstrapRuntime>>,
) -> bool {
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
        "open" => runtime.score(&viewer, candidate),
        _ => runtime.score_with_minimum_score(&viewer, candidate, 10),
    };
    decision.is_some_and(|decision| !decision.hide)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct PhotoPost {
    id: String,
    author_pubkey: String,
    author_name: Option<String>,
    author_avatar: Option<String>,
    images: Vec<ImageMeta>,
    caption: String,
    hashtags: Vec<String>,
    reaction_count: u32,
    comment_count: u32,
    zap_total: u64,
    created_at: u64,
    is_liked: bool,
    is_bookmarked: bool,
}

impl PhotoPost {
    fn from_event(event: &KernelEvent) -> Option<Self> {
        let images = parse_imeta_tags(&event.tags);
        if images.is_empty() {
            return None;
        }
        let hashtags = event
            .tags
            .iter()
            .filter_map(|tag| {
                if tag.first().is_some_and(|name| name == "t") {
                    tag.get(1).cloned()
                } else {
                    None
                }
            })
            .collect();

        Some(Self {
            id: event.id.clone(),
            author_pubkey: event.author.clone(),
            author_name: None,
            author_avatar: None,
            images,
            caption: event.content.clone(),
            hashtags,
            reaction_count: 0,
            comment_count: 0,
            zap_total: 0,
            created_at: event.created_at,
            is_liked: false,
            is_bookmarked: false,
        })
    }
}

#[derive(Serialize)]
struct ImageMeta {
    url: String,
    sha256: String,
    mime: String,
    width: Option<u32>,
    height: Option<u32>,
    blurhash: Option<String>,
    alt: Option<String>,
}

fn parse_imeta_tags(tags: &[Vec<String>]) -> Vec<ImageMeta> {
    tags.iter()
        .filter(|tag| tag.first().is_some_and(|name| name == "imeta"))
        .filter_map(|tag| parse_imeta(tag.iter().skip(1)))
        .collect()
}

fn parse_imeta<'a>(fields: impl Iterator<Item = &'a String>) -> Option<ImageMeta> {
    let mut url = None;
    let mut sha256 = String::new();
    let mut mime = "image/jpeg".to_string();
    let mut width = None;
    let mut height = None;
    let mut blurhash = None;
    let mut alt = None;

    for field in fields {
        let Some((key, value)) = field.split_once(' ') else {
            continue;
        };
        match key {
            "url" => url = Some(value.to_string()),
            "x" => sha256 = value.to_string(),
            "m" => mime = value.to_string(),
            "dim" => {
                if let Some((w, h)) = value.split_once('x') {
                    width = w.parse().ok();
                    height = h.parse().ok();
                }
            }
            "blurhash" => blurhash = Some(value.to_string()),
            "alt" => alt = Some(value.to_string()),
            _ => {}
        }
    }

    Some(ImageMeta {
        url: url?,
        sha256,
        mime,
        width,
        height,
        blurhash,
        alt,
    })
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
#[path = "photo_feed_tests.rs"]
mod tests;
