// jni_extras.rs — Android JNI wrappers for new Olas FFI functions.
//
// Each function is a thin transport shim: convert JNI types → C types,
// call the corresponding olas_* symbol in extras.rs, convert result back.
// No business logic lives here.

#![allow(non_snake_case)]
#![cfg(target_os = "android")]

use std::ffi::CString;

use jni::objects::{JClass, JString};
use jni::sys::{jdouble, jint, jlong, jstring};
use jni::JNIEnv;

use nmp_ffi::nmp_free_string;

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/// Convert a Rust-owned `*mut c_char` into a Java String and free the C string.
/// NULL in → NULL out.
unsafe fn cstring_into_jstring(env: &mut JNIEnv, raw: *mut std::os::raw::c_char) -> jstring {
    if raw.is_null() {
        return std::ptr::null_mut();
    }
    let s = std::ffi::CStr::from_ptr(raw).to_string_lossy().into_owned();
    nmp_free_string(raw);
    match env.new_string(&s) {
        Ok(j) => j.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Obtain a `CString` from a `JString`. Returns `None` on null or conversion error.
fn jstring_to_cstring(env: &mut JNIEnv, s: &JString) -> Option<CString> {
    let obj: &jni::objects::JObject = s.as_ref();
    if obj.is_null() {
        return None;
    }
    let java_str = env.get_string(s).ok()?;
    CString::new(java_str.to_string_lossy().as_bytes()).ok()
}

// ---------------------------------------------------------------------------
// JNI exports
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeKind20EventJson(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    event_json: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(input_c) = jstring_to_cstring(&mut env, &event_json) else {
            return std::ptr::null_mut();
        };
        let raw = crate::olas_decode_kind20_event_json(input_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeKind0EventJson(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    event_json: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(input_c) = jstring_to_cstring(&mut env, &event_json) else {
            return std::ptr::null_mut();
        };
        let raw = crate::olas_decode_kind0_event_json(input_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBolt11AmountMsats(
    mut env: JNIEnv,
    _class: JClass,
    bolt11: JString,
) -> jlong {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(input_c) = jstring_to_cstring(&mut env, &bolt11) else {
            return -1_i64;
        };
        crate::olas_bolt11_amount_msats(input_c.as_ptr())
    }));
    result.unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeComputeGeohash(
    mut env: JNIEnv,
    _class: JClass,
    lat: jdouble,
    lon: jdouble,
    precision: jint,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_compute_geohash(lat, lon, precision);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBuildZapActionJson(
    mut env: JNIEnv,
    _class: JClass,
    event_id: JString,
    sats: jlong,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(id_c) = jstring_to_cstring(&mut env, &event_id) else {
            return std::ptr::null_mut();
        };
        let raw = crate::olas_build_zap_action_json(id_c.as_ptr(), sats);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeFilterCatalogJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_filter_catalog_json();
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeMediaUploadConfigJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_media_upload_config_json();
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativePickerConfigJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_picker_config_json();
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeSettingsCatalogJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_settings_catalog_json();
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeOnboardingStepsJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_onboarding_steps_json();
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeComposeStepsJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_compose_steps_json();
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBlossomServerUrlGet(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_blossom_server_url_get(std::ptr::null_mut());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBlossomServerUrlSet(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    url: JString,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(url_c) = jstring_to_cstring(&mut env, &url) else { return };
        crate::olas_blossom_server_url_set(std::ptr::null_mut(), url_c.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeFeedModeGet(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = crate::olas_feed_mode_get(std::ptr::null_mut());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeFeedModeSet(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    mode: JString,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mode_c) = jstring_to_cstring(&mut env, &mode) else { return };
        crate::olas_feed_mode_set(std::ptr::null_mut(), mode_c.as_ptr());
    }));
}

// ── P0-E: Social proof ───────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeSocialProofJson(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    active_pubkey: JString,
    target_pubkey: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(active_c) = jstring_to_cstring(&mut env, &active_pubkey) else {
            return std::ptr::null_mut();
        };
        let Some(target_c) = jstring_to_cstring(&mut env, &target_pubkey) else {
            return std::ptr::null_mut();
        };
        let raw = crate::olas_social_proof_json(
            std::ptr::null_mut(),
            active_c.as_ptr(),
            target_c.as_ptr(),
        );
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ── P0-F: Discover sections ──────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDiscoverSectionsJson(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    active_pubkey: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(active_c) = jstring_to_cstring(&mut env, &active_pubkey) else {
            return std::ptr::null_mut();
        };
        let raw =
            crate::olas_discover_sections_json(std::ptr::null_mut(), active_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}
