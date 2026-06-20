// extras_ffi.rs — New Olas FFI helpers: event decoders, BOLT11, geohash,
// config providers. Mutable app-state (Blossom URL, feed mode) lives in
// extras_state.rs to stay within the 500-line file-size ceiling.

use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use nmp_core::substrate::KernelEvent;

// ─── Event decoders ──────────────────────────────────────────────────────────

/// Decodes a KernelEvent JSON string for a kind:20 picture event into a
/// `PictureEventRecord` JSON string (nmp_nip68 canonical shape).
/// Returns NULL if the event is not a valid NIP-68 kind:20 with at least one imeta image.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_decode_kind20_event_json(event_json: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if event_json.is_null() {
            return std::ptr::null_mut();
        }
        let json_str = match unsafe { CStr::from_ptr(event_json) }.to_str() {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let event: KernelEvent = match serde_json::from_str(json_str) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };
        let record = match nmp_nip68::try_from_kernel_event(&event) {
            Some(r) => r,
            None => return std::ptr::null_mut(),
        };
        match CString::new(serde_json::to_string(&record).unwrap_or_default()) {
            Ok(c) => c.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Parses a raw Nostr kind:0 event JSON and returns an OlasProfile JSON string.
/// Returns NULL if event is not kind:0.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_decode_kind0_event_json(event_json: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if event_json.is_null() {
            return std::ptr::null_mut();
        }
        let json_str = match unsafe { CStr::from_ptr(event_json) }.to_str() {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let event: serde_json::Value = match serde_json::from_str(json_str) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };

        // Verify kind:0
        if event.get("kind").and_then(|v| v.as_i64()) != Some(0) {
            return std::ptr::null_mut();
        }

        let pubkey = event
            .get("author")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        let content_str = event
            .get("content")
            .and_then(|v| v.as_str())
            .unwrap_or("{}");
        let content: serde_json::Value =
            serde_json::from_str(content_str).unwrap_or(serde_json::json!({}));

        let null_or_str = |key: &str| -> serde_json::Value {
            match content.get(key).and_then(|v| v.as_str()) {
                Some(s) if !s.is_empty() => serde_json::Value::String(s.to_string()),
                _ => serde_json::Value::Null,
            }
        };

        let profile = serde_json::json!({
            "pubkey": pubkey,
            "name": null_or_str("name"),
            "displayName": null_or_str("display_name"),
            "about": null_or_str("about"),
            "picture": null_or_str("picture"),
            "nip05": null_or_str("nip05"),
            "lud16": null_or_str("lud16"),
        });

        match serde_json::to_string(&profile) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ─── BOLT11 parsing ──────────────────────────────────────────────────────────

/// Parses a BOLT11 invoice string and returns the amount in millisatoshis, or -1 on error.
/// Pure parsing — no NmpApp pointer.
#[no_mangle]
pub extern "C" fn olas_bolt11_amount_msats(bolt11: *const c_char) -> i64 {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> i64 {
        if bolt11.is_null() {
            return -1;
        }
        let s = match unsafe { CStr::from_ptr(bolt11) }.to_str() {
            Ok(s) => s.to_lowercase(),
            Err(_) => return -1,
        };
        // BOLT11 starts with "ln"
        if !s.starts_with("ln") {
            return -1;
        }
        // The HRP is everything before the last '1' (bech32 separator)
        let sep_pos = match s.rfind('1') {
            Some(p) if p > 2 => p,
            _ => return -1,
        };
        let hrp = &s[..sep_pos];
        // hrp = "ln" + currency + amount_with_optional_unit
        let after_ln = &hrp[2..];
        // Currency is lowercase alpha chars at the start (e.g. "bc", "tb")
        let currency_end = after_ln
            .find(|c: char| c.is_ascii_digit())
            .unwrap_or(after_ln.len());
        let amount_part = &after_ln[currency_end..];
        if amount_part.is_empty() {
            // No amount encoded — return -1
            return -1;
        }
        // The last char may be a unit multiplier
        let last = amount_part.chars().last().unwrap();
        let (digits, unit) = if last.is_ascii_digit() {
            (amount_part, None)
        } else {
            (
                &amount_part[..amount_part.len() - last.len_utf8()],
                Some(last),
            )
        };
        let amount: i64 = match digits.parse() {
            Ok(n) => n,
            Err(_) => return -1,
        };
        match unit {
            None => {
                // Whole BTC → msats
                amount.checked_mul(100_000_000_000).unwrap_or(-1)
            }
            Some('m') => amount.checked_mul(100_000_000).unwrap_or(-1),
            Some('u') => amount.checked_mul(100_000).unwrap_or(-1),
            Some('n') => amount.checked_mul(100).unwrap_or(-1),
            Some('p') => {
                // pico-BTC: amount / 10 msats (floor to 0)
                (amount / 10).max(0)
            }
            _ => -1,
        }
    }));
    result.unwrap_or(-1)
}

// ─── Geohash ─────────────────────────────────────────────────────────────────

/// Computes a geohash string of the given precision (1–12).
/// Returns NULL on invalid input.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_compute_geohash(lat: f64, lon: f64, precision: i32) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if precision <= 0 || precision > 12 {
            return std::ptr::null_mut();
        }
        if !(-90.0..=90.0).contains(&lat) || !(-180.0..=180.0).contains(&lon) {
            return std::ptr::null_mut();
        }
        const BASE32: &[u8] = b"0123456789bcdefghjkmnpqrstuvwxyz";
        let precision = precision as usize;
        let mut hash = String::with_capacity(precision);
        let mut lat_min = -90.0_f64;
        let mut lat_max = 90.0_f64;
        let mut lon_min = -180.0_f64;
        let mut lon_max = 180.0_f64;
        let mut bits = 0u8;
        let mut bit_count = 0u8;
        let mut use_lon = true; // start with longitude

        while hash.len() < precision {
            let is_right = if use_lon {
                let mid = (lon_min + lon_max) / 2.0;
                if lon >= mid {
                    lon_min = mid;
                    true
                } else {
                    lon_max = mid;
                    false
                }
            } else {
                let mid = (lat_min + lat_max) / 2.0;
                if lat >= mid {
                    lat_min = mid;
                    true
                } else {
                    lat_max = mid;
                    false
                }
            };
            bits = (bits << 1) | (if is_right { 1 } else { 0 });
            bit_count += 1;
            use_lon = !use_lon;

            if bit_count == 5 {
                hash.push(BASE32[bits as usize] as char);
                bits = 0;
                bit_count = 0;
            }
        }

        CString::new(hash)
            .map(|cs| cs.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ─── Zap action ──────────────────────────────────────────────────────────────

/// Builds the JSON body for the nmp.nip57.zap dispatch action.
/// sats is converted to msats (×1000) in Rust.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_build_zap_action_json(event_id: *const c_char, sats: i64) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if event_id.is_null() {
            return std::ptr::null_mut();
        }
        let id = match unsafe { CStr::from_ptr(event_id) }.to_str() {
            Ok(s) if !s.is_empty() => s.to_string(),
            _ => return std::ptr::null_mut(),
        };
        let amount_msats = sats.saturating_mul(1000);
        let json = serde_json::json!({
            "event_id": id,
            "amount_msats": amount_msats,
            "comment": "",
        });
        match serde_json::to_string(&json) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ─── Config providers ────────────────────────────────────────────────────────

/// Returns a JSON array of available photo filter IDs and display names.
/// No NmpApp pointer needed. Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_filter_catalog_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let catalog = serde_json::json!([
            {"id": "original", "name": "Original"},
            {"id": "daylight",  "name": "Daylight"},
            {"id": "ember",     "name": "Ember"},
            {"id": "dusk",      "name": "Dusk"},
            {"id": "chrome",    "name": "Chrome"},
            {"id": "fade",      "name": "Fade"},
        ]);
        match serde_json::to_string(&catalog) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Returns the upload parameters owned by Rust.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_media_upload_config_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let config = serde_json::json!({
            "max_dimension": 2048,
            "jpeg_quality": 0.92,
            "strip_exif": true,
        });
        match serde_json::to_string(&config) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Returns photo picker constraints.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_picker_config_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let config = serde_json::json!({
            "max_selection": 1,
            "allowed_types": ["image"],
        });
        match serde_json::to_string(&config) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Returns the settings screen navigation catalog matching SettingsView.swift rows.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_settings_catalog_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let catalog = serde_json::json!([
            {"id": "account",          "label": "Account"},
            {"id": "notifications",    "label": "Notifications"},
            {"id": "content_filtering","label": "Content & Filtering"},
            {"id": "appearance",       "label": "Appearance"},
            {"id": "help",             "label": "Help"},
            {"id": "servers",          "label": "Servers"},
            {"id": "relays",           "label": "Relays"},
            {"id": "wallet_zaps",      "label": "Wallet & Zaps"},
            {"id": "account_security", "label": "Account Security"},
        ]);
        match serde_json::to_string(&catalog) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Returns the ordered list of onboarding step IDs matching OnboardingView.swift.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_onboarding_steps_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let steps = serde_json::json!([
            "welcome",
            "create_account",
            "sign_in",
            "media_server",
            "follow_packs",
            "complete",
        ]);
        match serde_json::to_string(&steps) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    });
    result.unwrap_or(std::ptr::null_mut())
}

/// Returns the ordered compose step IDs.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_compose_steps_json() -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> *mut c_char {
        let steps = serde_json::json!(["photo_picker", "edit_photo", "caption"]);
        match serde_json::to_string(&steps) {
            Ok(s) => CString::new(s)
                .map(|cs| cs.into_raw())
                .unwrap_or(std::ptr::null_mut()),
            Err(_) => std::ptr::null_mut(),
        }
    });
    result.unwrap_or(std::ptr::null_mut())
}

// Note: Blossom URL and feed mode state lives in extras_state.rs.

// ─── Zap notification decoder ─────────────────────────────────────────────────

/// Parses a raw Nostr kind:9735 zap receipt JSON and returns
/// {"amount_sats":N,"referenced_event_id":"..."} or NULL.
/// Returned string must be freed with nmp_free_string.
#[no_mangle]
pub extern "C" fn olas_decode_zap_notification_json(event_json: *const c_char) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> *mut c_char {
        if event_json.is_null() {
            return std::ptr::null_mut();
        }
        let input = match unsafe { CStr::from_ptr(event_json) }.to_str() {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let event: serde_json::Value = match serde_json::from_str(input) {
            Ok(v) => v,
            Err(_) => return std::ptr::null_mut(),
        };
        // Only handle kind:9735 zap receipts
        if event["kind"].as_i64() != Some(9735) {
            return std::ptr::null_mut();
        }
        let tags = match event["tags"].as_array() {
            Some(t) => t,
            None => return std::ptr::null_mut(),
        };
        // Extract bolt11 tag → msats → sats
        let bolt11 = tags
            .iter()
            .find(|t| {
                t.as_array()
                    .and_then(|a| a.first())
                    .and_then(|v| v.as_str())
                    == Some("bolt11")
            })
            .and_then(|t| t.as_array()?.get(1)?.as_str())
            .unwrap_or("");
        // Parse bolt11 for msats using the logic from olas_bolt11_amount_msats
        let amount_msats = if bolt11.is_empty() {
            0i64
        } else {
            let b = bolt11.to_lowercase();
            // Find the separator '1' after the HRP
            let sep = b.find('1').unwrap_or(0);
            let hrp = &b[..sep];
            // Strip "ln" + currency (bc, bcrt, tb, sb)
            let amount_part = hrp
                .strip_prefix("ln")
                .and_then(|s| {
                    ["bcrt", "bc", "tb", "sb"]
                        .iter()
                        .filter_map(|c| s.strip_prefix(c))
                        .next()
                })
                .unwrap_or("");
            if amount_part.is_empty() {
                0
            } else {
                let last = amount_part.chars().last().unwrap_or('0');
                let digits: &str = if last.is_ascii_alphabetic() {
                    &amount_part[..amount_part.len() - last.len_utf8()]
                } else {
                    amount_part
                };
                let n: i64 = digits.parse().unwrap_or(0);
                match last {
                    'm' => n * 100_000_000i64,
                    'u' => n * 100_000i64,
                    'n' => n * 100i64,
                    'p' => n / 10i64,
                    _ => n * 100_000_000_000i64, // whole BTC
                }
            }
        };
        let amount_sats = if amount_msats > 0 {
            amount_msats / 1000
        } else {
            0
        };
        // Extract referenced event id (first "e" tag)
        let referenced_event_id = tags
            .iter()
            .find(|t| {
                t.as_array()
                    .and_then(|a| a.first())
                    .and_then(|v| v.as_str())
                    == Some("e")
            })
            .and_then(|t| t.as_array()?.get(1)?.as_str())
            .unwrap_or("");
        let output = serde_json::json!({
            "amount_sats": amount_sats,
            "referenced_event_id": referenced_event_id,
        });
        match CString::new(output.to_string()) {
            Ok(c) => c.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}
