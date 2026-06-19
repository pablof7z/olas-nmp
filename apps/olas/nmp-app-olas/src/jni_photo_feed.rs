#![cfg(target_os = "android")]

use std::ffi::{CStr, CString};

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jlong, jstring};
use jni::JNIEnv;
use nmp_ffi::nmp_free_string;

use crate::olas_filter_photo_post_json;

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeFilterPhotoPostJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    event_json: JString,
    contact_list_only: jboolean,
    wot_preset: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(event) = jstring_to_cstring(&mut env, &event_json) else {
            return std::ptr::null_mut();
        };
        let preset = jstring_to_cstring(&mut env, &wot_preset)
            .unwrap_or_else(|| CString::new("balanced").unwrap());
        let raw = olas_filter_photo_post_json(
            event.as_ptr(),
            if contact_list_only != 0 { 1 } else { 0 },
            preset.as_ptr(),
        );
        unsafe { cstring_into_jstring(&mut env, raw) }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn jstring_to_cstring(env: &mut JNIEnv, s: &JString) -> Option<CString> {
    let obj: &JObject = s.as_ref();
    if obj.is_null() {
        return None;
    }
    let java_str = env.get_string(s).ok()?;
    CString::new(java_str.to_string_lossy().as_bytes()).ok()
}

unsafe fn cstring_into_jstring(env: &mut JNIEnv, raw: *mut std::os::raw::c_char) -> jstring {
    if raw.is_null() {
        return std::ptr::null_mut();
    }
    let s = CStr::from_ptr(raw).to_string_lossy().into_owned();
    nmp_free_string(raw);
    match env.new_string(&s) {
        Ok(j) => j.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
