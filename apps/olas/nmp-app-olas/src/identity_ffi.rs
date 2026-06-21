// identity_ffi.rs — P3-D: Account recovery key export.
//
// Exports the active account's secret key as a copyable bech32 string.
//
// SECURITY CONTRACT:
//   1. This function MUST NOT log the returned key at any log level.
//   2. The caller (iOS / Android) must zero or release the memory promptly
//      after copying the key to the clipboard or display.
//   3. Expose only through an explicit user-initiated "Back up account" flow.
//   4. The user-facing name is ALWAYS "Recovery Key" — never "nsec",
//      "private key", or "seed". This invariant is enforced by the UI layer.

use std::ffi::CString;
use std::os::raw::c_char;

use nmp_ffi::NmpApp;

/// Export the active account's secret key as a Recovery Key string.
///
/// Returns the bech32-encoded secret key for the active local account,
/// or `NULL` when no local account is signed in (remote signer or no account).
///
/// The returned string is in `nsec1…` bech32 format — this is the Recovery Key
/// that the user should store safely. Present it to the user only under explicit
/// request (the "Back up account" flow) and NEVER show the raw term "nsec".
///
/// Returned string must be freed with `nmp_free_string`. Do NOT log the value.
#[no_mangle]
pub extern "C" fn olas_active_account_recovery_key(app: *mut NmpApp) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> *mut c_char {
            if app.is_null() {
                return std::ptr::null_mut();
            }
            // SAFETY: caller guarantees a valid NmpApp pointer from nmp_app_new.
            // NmpApp is Send+Sync; we only read from a slot protected by Mutex.
            let app_ref = unsafe { &*app };
            let key = match app_ref.mls_local_nsec() {
                Some(k) => k,
                None => return std::ptr::null_mut(),
            };
            // MUST NOT log `key` — it is the raw secret key.
            CString::new(key.as_str())
                .map(CString::into_raw)
                .unwrap_or(std::ptr::null_mut())
        },
    ));
    result.unwrap_or(std::ptr::null_mut())
}
