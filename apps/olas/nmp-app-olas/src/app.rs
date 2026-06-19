use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use crate::{event_models, photo_feed, NmpApp};

/// Register Olas-specific protocol extensions on a freshly constructed NmpApp.
#[no_mangle]
pub extern "C" fn olas_app_register(app: *mut NmpApp) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        // SAFETY: caller provides a valid pointer from nmp_app_new.
        let app_ref = unsafe { &mut *app };
        let handles = nmp_defaults::register_defaults_with_handles(
            app_ref,
            nmp_defaults::NmpDefaults::default(),
        );
        photo_feed::install_wot_runtime(handles.wot);
        nmp_blossom::register_actions(app_ref);
        event_models::olas_add_default_relays(app);
    }));
}

/// Open a NIP-68 (kind:20) photo feed subscription.
///
/// `contact_list_only`: 1 = Following feed, 0 = Network feed. Network events are
/// filtered by `olas_filter_photo_post_json` before becoming Olas view models.
#[no_mangle]
pub extern "C" fn olas_open_photo_feed(
    app: *mut NmpApp,
    contact_list_only: u8,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let consumer = if consumer_id.is_null() {
            "olas.photo_feed".to_string()
        } else {
            unsafe { CStr::from_ptr(consumer_id) }
                .to_str()
                .unwrap_or("olas.photo_feed")
                .to_string()
        };

        let filter_json = if contact_list_only != 0 {
            r#"{"kinds":[20],"limit":50}"#
        } else {
            r#"{"kinds":[20],"limit":100}"#
        };
        let scope: u32 = if contact_list_only != 0 { 0 } else { 1 };
        let Ok(filter_cstr) = CString::new(filter_json) else {
            return;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_ffi::nmp_app_open_interest(app, filter_cstr.as_ptr(), consumer_cstr.as_ptr(), scope);
    }));
}

/// Open a NIP-51 kind:30000 follow pack by NIP-19 address or bare coordinate.
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
        let Ok(addr_cstr) = CString::new(addr) else {
            return;
        };
        nmp_ffi::nmp_app_open_uri(app, addr_cstr.as_ptr());
    }));
}
