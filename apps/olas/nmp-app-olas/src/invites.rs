// invites.rs — P2-A/P2-C: invite link resolution and minting.
//
// Token format (single source of truth — token logic lives only here):
//   Canonical link: https://olas.app/i/<npub>
//   Custom scheme:  olas://i/<npub>
//   Bare token:     npub1... or 64-char hex pubkey
//
// olas_resolve_invite_json — decode any invite token form into inviter info.
// olas_my_invite_link     — mint the canonical shareable link for the active user.
//
// Native (Swift/Kotlin) only handles URL-scheme plumbing and share sheet. All
// token encoding/decoding lives here so format changes need no native edits.

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

/// Resolve an invite token → inviter info.
///
/// `token` accepts any of:
///   - Full HTTPS link: `https://olas.app/i/<npub>`
///   - Custom scheme:   `olas://i/<npub>`
///   - Bare NIP-19 npub: `npub1...`
///   - Raw 64-char hex pubkey (fallback, not normal path)
///
/// Returns: `{"inviter_pubkey":"<hex>","display_hint":"npub1..."}` or NULL.
/// Returns NULL when the token cannot be resolved to a valid pubkey.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_resolve_invite_json(token: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if token.is_null() {
            return std::ptr::null_mut();
        }
        let raw = match unsafe { CStr::from_ptr(token) }.to_str() {
            Ok(s) if !s.is_empty() => s,
            _ => return std::ptr::null_mut(),
        };

        let bare = extract_invite_token(raw);
        if bare.is_empty() {
            return std::ptr::null_mut();
        }

        let hex_pubkey = match resolve_to_hex(bare) {
            Some(h) => h,
            None => return std::ptr::null_mut(),
        };

        let npub = nmp_core::display::to_npub(&hex_pubkey);
        let out = serde_json::json!({
            "inviter_pubkey": hex_pubkey,
            "display_hint": npub,
        });
        let Ok(json) = serde_json::to_string(&out) else {
            return std::ptr::null_mut();
        };
        CString::new(json)
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Mint the canonical invite link for the active user.
///
/// `active_pubkey` — the active account's 64-char hex pubkey.
/// Returns: `"https://olas.app/i/<npub>"` or NULL.
/// Returns NULL when the pubkey is NULL, empty, or cannot be encoded.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_my_invite_link(active_pubkey: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        if active_pubkey.is_null() {
            return std::ptr::null_mut();
        }
        let hex = match unsafe { CStr::from_ptr(active_pubkey) }.to_str() {
            Ok(s) if s.len() == 64 && s.chars().all(|c| c.is_ascii_hexdigit()) => s,
            _ => return std::ptr::null_mut(),
        };
        let npub = nmp_core::display::to_npub(hex);
        if !npub.starts_with("npub1") {
            return std::ptr::null_mut();
        }
        let link = format!("https://olas.app/i/{}", npub);
        CString::new(link)
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut())
    });
    result.unwrap_or(std::ptr::null_mut())
}

// ── Internal helpers ──────────────────────────────────────────────────────────

/// Strip the URL wrapper from an invite link, returning the bare token.
fn extract_invite_token(input: &str) -> &str {
    if let Some(rest) = input.strip_prefix("https://olas.app/i/") {
        return rest.trim_matches('/');
    }
    if let Some(rest) = input.strip_prefix("olas://i/") {
        return rest.trim_matches('/');
    }
    input.trim()
}

/// Decode a bare token (npub or hex) to a 64-char hex pubkey string.
fn resolve_to_hex(token: &str) -> Option<String> {
    if token.starts_with("npub1") {
        return nmp_core::nip19::decode_npub(token).ok();
    }
    // Accept raw hex pubkey as a fallback.
    if token.len() == 64 && token.chars().all(|c| c.is_ascii_hexdigit()) {
        return Some(token.to_lowercase());
    }
    None
}
