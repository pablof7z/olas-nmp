// extras_state.rs — Mutable app-state getters/setters: Blossom server URL and
// feed mode. Split from extras_ffi.rs to stay within the 500-line ceiling.

use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::{Mutex, OnceLock};

use nmp_ffi::NmpApp;

// ─── Blossom URL global storage ──────────────────────────────────────────────

static BLOSSOM_URL: OnceLock<Mutex<String>> = OnceLock::new();

fn blossom_url_store() -> &'static Mutex<String> {
    BLOSSOM_URL.get_or_init(|| Mutex::new("https://blossom.primal.net".to_string()))
}

/// Returns the current primary Blossom server URL.
/// Defaults to "https://blossom.primal.net".
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_blossom_server_url_get(_app: *mut NmpApp) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        let url = blossom_url_store()
            .lock()
            .map(|g| g.clone())
            .unwrap_or_else(|_| "https://blossom.primal.net".to_string());
        let s = if url.is_empty() {
            "https://blossom.primal.net".to_string()
        } else {
            url
        };
        CString::new(s)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Sets the primary Blossom server URL.
/// Ignores null url or empty string (keeps current value).
#[no_mangle]
pub extern "C" fn olas_blossom_server_url_set(_app: *mut NmpApp, url: *const c_char) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if url.is_null() {
            return;
        }
        let s = match unsafe { CStr::from_ptr(url) }.to_str() {
            Ok(s) if !s.is_empty() => s.to_string(),
            _ => return,
        };
        if let Ok(mut guard) = blossom_url_store().lock() {
            *guard = s;
        }
    }));
}

// ─── Feed mode global storage ────────────────────────────────────────────────

static FEED_MODE: OnceLock<Mutex<String>> = OnceLock::new();

fn feed_mode_store() -> &'static Mutex<String> {
    FEED_MODE.get_or_init(|| Mutex::new("network".to_string()))
}

/// Returns the current active feed mode: "following" or "network".
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_feed_mode_get(_app: *mut NmpApp) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        let mode = feed_mode_store()
            .lock()
            .map(|g| g.clone())
            .unwrap_or_else(|_| "network".to_string());
        CString::new(mode)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Sets the active feed mode. Only accepts "following" or "network"; ignores other values.
#[no_mangle]
pub extern "C" fn olas_feed_mode_set(_app: *mut NmpApp, mode: *const c_char) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if mode.is_null() {
            return;
        }
        let s = match unsafe { CStr::from_ptr(mode) }.to_str() {
            Ok(s) => s,
            Err(_) => return,
        };
        if s != "following" && s != "network" {
            return;
        }
        if let Ok(mut guard) = feed_mode_store().lock() {
            *guard = s.to_string();
        }
    }));
}
