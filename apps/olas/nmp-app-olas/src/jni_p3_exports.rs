// jni_p3_exports.rs — Android JNI shims for P3-B / P3-C / P3-D FFI functions.
//
// Thin transport layer only: convert JNI ↔ C types, delegate to Rust FFI symbols,
// return result. Zero business logic lives here.

#![cfg(target_os = "android")]
#![allow(non_snake_case)]

use std::ffi::CString;

use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;

use nmp_ffi::nmp_free_string;

// ── helpers (duplicated from jni_extras.rs for module independence) ──────────

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

fn jstring_to_cstring(env: &mut JNIEnv, s: &JString) -> Option<CString> {
    let obj: &jni::objects::JObject = s.as_ref();
    if obj.is_null() {
        return None;
    }
    let java_str = env.get_string(s).ok()?;
    CString::new(java_str.to_string_lossy().as_bytes()).ok()
}

// ── P3-B: grouped notifications ──────────────────────────────────────────────

/// Group a JSON array of individual notification payloads into clustered rows.
/// Thin JNI shim for `olas_group_notifications_json`.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeGroupNotificationsJson(
    mut env: JNIEnv,
    _class: JClass,
    notifications_json: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(input_c) = jstring_to_cstring(&mut env, &notifications_json) else {
            return std::ptr::null_mut();
        };
        let raw = crate::olas_group_notifications_json(input_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ── P3-C: caption tag parsing ────────────────────────────────────────────────

/// Parse `nostr:npub1…` mentions and `#hashtag` tokens from a caption.
/// Thin JNI shim for `olas_parse_caption_tags_json`.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeParseCaptionTagsJson(
    mut env: JNIEnv,
    _class: JClass,
    caption: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(caption_c) = jstring_to_cstring(&mut env, &caption) else {
            return std::ptr::null_mut();
        };
        let raw = crate::olas_parse_caption_tags_json(caption_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Extended picture-post publish that injects p/t tags.
/// Thin JNI shim for `olas_picture_post_publish_tagged_json`.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativePicturePostPublishTaggedJson(
    mut env: JNIEnv,
    _class: JClass,
    uploaded_images_json: JString,
    caption: JString,
    geohash: JString,
    extra_tags_json: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(images_c) = jstring_to_cstring(&mut env, &uploaded_images_json) else {
            return std::ptr::null_mut();
        };
        let caption_c = jstring_to_cstring(&mut env, &caption);
        let geohash_c = jstring_to_cstring(&mut env, &geohash);
        let extra_c = jstring_to_cstring(&mut env, &extra_tags_json);

        let caption_ptr = caption_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let geohash_ptr = geohash_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let extra_ptr = extra_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());

        let raw = crate::olas_picture_post_publish_tagged_json(
            images_c.as_ptr(),
            caption_ptr,
            geohash_ptr,
            extra_ptr,
        );
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ── P3-D: recovery key export ─────────────────────────────────────────────────

/// Export the active account's Recovery Key (bech32 secret).
/// Thin JNI shim for `olas_active_account_recovery_key`.
/// MUST NOT be logged by the caller.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeActiveAccountRecoveryKey(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        // Unpack the (app_ptr, ctx_ptr) pair stored behind `handle`.
        let pair = &*(handle as *const (usize, usize));
        let app = pair.0 as *mut nmp_ffi::NmpApp;
        let raw = crate::olas_active_account_recovery_key(app);
        // MUST NOT log `raw` — it is the raw secret key.
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}
