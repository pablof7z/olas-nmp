#![cfg(target_os = "android")]

use std::ffi::{CStr, CString};

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
use nmp_ffi::nmp_free_string;

use crate::{
    olas_contact_list_pubkeys_json, olas_default_relays_json, olas_notification_json,
    olas_profile_json,
};

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeProfileJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    event_json: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    string_event_export(&mut env, &event_json, olas_profile_json)
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeNotificationJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    event_json: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    string_event_export(&mut env, &event_json, olas_notification_json)
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeContactListPubkeysJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    event_json: JString,
    active_pubkey: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(event) = jstring_to_cstring(&mut env, &event_json) else {
            return std::ptr::null_mut();
        };
        let Some(active) = jstring_to_cstring(&mut env, &active_pubkey) else {
            return std::ptr::null_mut();
        };
        let raw = olas_contact_list_pubkeys_json(event.as_ptr(), active.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDefaultRelaysJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        cstring_into_jstring(&mut env, olas_default_relays_json())
    }));
    result.unwrap_or(std::ptr::null_mut())
}

fn string_event_export(
    env: &mut JNIEnv,
    event_json: &JString,
    f: extern "C" fn(*const std::os::raw::c_char) -> *mut std::os::raw::c_char,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(event) = jstring_to_cstring(env, event_json) else {
            return std::ptr::null_mut();
        };
        cstring_into_jstring(env, f(event.as_ptr()))
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
