// extras.rs — Olas-specific helper FFI functions.
// Relay seeding, NIP-50 search interest management, and account creation in
// Rust (no Swift/Kotlin policy).
// New event-decoding and config helpers live in extras_ffi.rs (kept within 500-line ceiling).

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use nmp_ffi::{NmpApp, NmpConfigStatus};

use crate::event_models::default_relay_rows;

fn default_relay_config() -> Vec<(String, String)> {
    default_relay_rows()
        .map(|(url, role)| (url.to_string(), role.to_string()))
        .collect()
}

pub(crate) fn declare_initial_relays(app: &NmpApp) -> NmpConfigStatus {
    app.set_initial_relays_for_start(default_relay_config())
}

/// Declare Olas' canonical initial relay set before nmp_app_start.
///
/// Normal app startup should call olas_app_register, which invokes this helper.
/// The exported symbol exists for native shells that need to make the lifecycle
/// edge explicit.
#[no_mangle]
pub extern "C" fn olas_declare_initial_relays(app: *mut NmpApp) -> u32 {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return NmpConfigStatus::NullApp;
        }
        // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
        let app_ref = unsafe { &*app };
        declare_initial_relays(app_ref)
    }));
    result.unwrap_or(NmpConfigStatus::Unavailable).code()
}

/// Seed the four canonical Olas relays on a running NmpApp.
///
/// Normal startup declares these relays before nmp_app_start through
/// olas_app_register. Call this only for an explicit relay-config reset.
/// Adds: relay.damus.io, nos.lol, relay.primal.net (role "both") and
/// purplepag.es (role "indexer" — purpose-built kind:0 profile index).
#[no_mangle]
pub extern "C" fn olas_seed_default_relays(app: *mut NmpApp) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        for (url, role) in default_relay_rows() {
            let Ok(url_c) = CString::new(url) else {
                continue;
            };
            let Ok(role_c) = CString::new(role) else {
                continue;
            };
            nmp_ffi::nmp_app_add_relay(app, url_c.as_ptr(), role_c.as_ptr());
        }
    }));
}

/// Open a mixed NIP-50 search interest (profiles + picture events).
///
/// This is not the typed picture-feed projection: it is a search query surface
/// whose results include kind:0 profiles and primary kind:20 picture events.
/// consumer_id identifies this subscription; close with
/// olas_close_search_feed using the same query and consumer_id. Scope 1 =
/// global.
#[no_mangle]
pub extern "C" fn olas_open_search_feed(
    app: *mut NmpApp,
    query: *const c_char,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() || query.is_null() {
            return;
        }
        let q = match unsafe { CStr::from_ptr(query) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return,
        };
        let consumer = if consumer_id.is_null() {
            "olas.search".to_string()
        } else {
            unsafe { CStr::from_ptr(consumer_id) }
                .to_str()
                .unwrap_or("olas.search")
                .to_string()
        };
        let filter = match serde_json::to_string(&serde_json::json!({
            "kinds": [0, 20],
            "search": q,
            "limit": 50
        })) {
            Ok(s) => s,
            Err(_) => return,
        };
        let Ok(filter_c) = CString::new(filter) else {
            return;
        };
        let Ok(consumer_c) = CString::new(consumer) else {
            return;
        };
        nmp_ffi::nmp_app_open_interest(app, filter_c.as_ptr(), consumer_c.as_ptr(), 1);
    }));
}

/// Close a NIP-50 search interest opened with olas_open_search_feed.
///
/// Must be called with the same query and consumer_id as the matching open call.
#[no_mangle]
pub extern "C" fn olas_close_search_feed(
    app: *mut NmpApp,
    query: *const c_char,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() || query.is_null() {
            return;
        }
        let q = match unsafe { CStr::from_ptr(query) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return,
        };
        let consumer = if consumer_id.is_null() {
            "olas.search".to_string()
        } else {
            unsafe { CStr::from_ptr(consumer_id) }
                .to_str()
                .unwrap_or("olas.search")
                .to_string()
        };
        let filter = match serde_json::to_string(&serde_json::json!({
            "kinds": [0, 20],
            "search": q,
            "limit": 50
        })) {
            Ok(s) => s,
            Err(_) => return,
        };
        let Ok(filter_c) = CString::new(filter) else {
            return;
        };
        let Ok(consumer_c) = CString::new(consumer) else {
            return;
        };
        nmp_ffi::nmp_app_close_interest(app, filter_c.as_ptr(), consumer_c.as_ptr(), 1);
    }));
}

/// Create a new account with the given display name and username (nip05 suffix).
///
/// Constructs profile JSON ({name, nip05: <username>@olas.app}) and calls
/// nmp_app_create_new_account with empty initial relays (relay seeding is
/// handled separately by olas_seed_default_relays). Passes make_active=1.
#[no_mangle]
pub extern "C" fn olas_create_account(
    app: *mut NmpApp,
    name: *const c_char,
    username: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        let name_str = if name.is_null() {
            String::new()
        } else {
            unsafe { CStr::from_ptr(name) }
                .to_str()
                .unwrap_or("")
                .to_string()
        };
        let username_str = if username.is_null() {
            String::new()
        } else {
            unsafe { CStr::from_ptr(username) }
                .to_str()
                .unwrap_or("")
                .to_string()
        };
        let profile = match serde_json::to_string(&serde_json::json!({
            "name": name_str,
            "nip05": format!("{}@olas.app", username_str),
        })) {
            Ok(s) => s,
            Err(_) => return,
        };
        let Ok(profile_c) = CString::new(profile) else {
            return;
        };
        let Ok(relays_c) = CString::new("[]") else {
            return;
        };
        nmp_ffi::nmp_app_create_new_account(app, profile_c.as_ptr(), relays_c.as_ptr(), false, 1);
    }));
}
