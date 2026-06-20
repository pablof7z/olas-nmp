#![cfg(target_os = "android")]

use std::ffi::{CStr, CString};

use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jlong, jstring};
use jni::JNIEnv;
use nmp_ffi::{
    nmp_app_claim_profile, nmp_app_dispatch_action, nmp_app_lifecycle_background,
    nmp_app_lifecycle_foreground, nmp_app_load_older_feed, nmp_app_open_contact_feed,
    nmp_app_register_event_observer, nmp_app_release_profile, nmp_free_string,
};

use crate::{
    olas_close_search_feed, olas_decode_snapshot_photo_feed_json, olas_open_photo_feed,
    olas_open_search_feed, olas_wot_preset_set,
};

use super::{
    cstring_into_jstring, jstring_to_cstring, on_event, unpack, EventObserverCtx,
    KernelEventObserverFn,
};

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeOpenContactFeed(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    kinds_json: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(kinds) = jstring_to_cstring(&mut env, &kinds_json) else {
            return;
        };
        nmp_app_open_contact_feed(app, kinds.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeOpenPhotoFeed(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    contact_list_only: jboolean,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let consumer = if contact_list_only != 0 {
            CString::new(crate::picture_feed::FOLLOWING_FEED_KEY).unwrap()
        } else {
            CString::new(crate::picture_feed::NETWORK_FEED_KEY).unwrap()
        };
        olas_open_photo_feed(
            app,
            if contact_list_only != 0 { 1 } else { 0 },
            consumer.as_ptr(),
        );
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeOpenSearchFeed(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query: JString,
    consumer_id: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(query) = jstring_to_cstring(&mut env, &query) else {
            return;
        };
        let Some(consumer) = jstring_to_cstring(&mut env, &consumer_id) else {
            return;
        };
        olas_open_search_feed(app, query.as_ptr(), consumer.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeCloseSearchFeed(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query: JString,
    consumer_id: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(query) = jstring_to_cstring(&mut env, &query) else {
            return;
        };
        let Some(consumer) = jstring_to_cstring(&mut env, &consumer_id) else {
            return;
        };
        olas_close_search_feed(app, query.as_ptr(), consumer.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDispatchAction(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    namespace: JString,
    action_json: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(ns) = jstring_to_cstring(&mut env, &namespace) else {
            return std::ptr::null_mut();
        };
        let Some(json) = jstring_to_cstring(&mut env, &action_json) else {
            return std::ptr::null_mut();
        };
        let raw = nmp_app_dispatch_action(app, ns.as_ptr(), json.as_ptr());
        if raw.is_null() {
            return std::ptr::null_mut();
        }
        let result = CStr::from_ptr(raw).to_string_lossy().into_owned();
        nmp_free_string(raw);
        match env.new_string(&result) {
            Ok(value) => value.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeRegisterEventObserver(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    listener: JObject,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        if listener.is_null() {
            return;
        }
        let Ok(vm) = env.get_java_vm() else { return };
        let Ok(global) = env.new_global_ref(&listener) else {
            return;
        };
        let ctx = Box::new(EventObserverCtx {
            vm,
            handler: global,
        });
        let ctx_ptr = Box::into_raw(ctx) as *mut std::ffi::c_void;
        nmp_app_register_event_observer(app, ctx_ptr, Some(on_event as KernelEventObserverFn));
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeActionResults(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    frame: JByteArray,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let bytes: Vec<u8> = match env.convert_byte_array(&frame) {
            Ok(bytes) => bytes,
            Err(_) => return std::ptr::null_mut(),
        };
        if bytes.is_empty() {
            return std::ptr::null_mut();
        }
        let raw = crate::olas_decode_snapshot_action_results_json(bytes.as_ptr(), bytes.len());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeActiveAccount(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    frame: JByteArray,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let bytes: Vec<u8> = match env.convert_byte_array(&frame) {
            Ok(bytes) => bytes,
            Err(_) => return std::ptr::null_mut(),
        };
        if bytes.is_empty() {
            return std::ptr::null_mut();
        }
        let raw = crate::olas_decode_snapshot_active_account_json(bytes.as_ptr(), bytes.len());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeClaimedProfiles(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    frame: JByteArray,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let bytes: Vec<u8> = match env.convert_byte_array(&frame) {
            Ok(bytes) => bytes,
            Err(_) => return std::ptr::null_mut(),
        };
        if bytes.is_empty() {
            return std::ptr::null_mut();
        }
        let raw = crate::olas_decode_snapshot_claimed_profiles_json(bytes.as_ptr(), bytes.len());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodePhotoFeed(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    frame: JByteArray,
    key: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let bytes: Vec<u8> = match env.convert_byte_array(&frame) {
            Ok(bytes) => bytes,
            Err(_) => return std::ptr::null_mut(),
        };
        if bytes.is_empty() {
            return std::ptr::null_mut();
        }
        let Some(key) = jstring_to_cstring(&mut env, &key) else {
            return std::ptr::null_mut();
        };
        let raw = olas_decode_snapshot_photo_feed_json(bytes.as_ptr(), bytes.len(), key.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeClaimProfile(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pubkey: JString,
    consumer_id: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(pubkey) = jstring_to_cstring(&mut env, &pubkey) else {
            return;
        };
        let Some(consumer_id) = jstring_to_cstring(&mut env, &consumer_id) else {
            return;
        };
        nmp_app_claim_profile(app, pubkey.as_ptr(), consumer_id.as_ptr(), 0, 0);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeReleaseProfile(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pubkey: JString,
    consumer_id: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(pubkey) = jstring_to_cstring(&mut env, &pubkey) else {
            return;
        };
        let Some(consumer_id) = jstring_to_cstring(&mut env, &consumer_id) else {
            return;
        };
        nmp_app_release_profile(app, pubkey.as_ptr(), consumer_id.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeWotPresetSet(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    preset: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(preset) = jstring_to_cstring(&mut env, &preset) else {
            return;
        };
        olas_wot_preset_set(app, preset.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeLoadOlderFeed(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    key: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(key) = jstring_to_cstring(&mut env, &key) else {
            return;
        };
        nmp_app_load_older_feed(app, key.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeLifecycleForeground(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        nmp_app_lifecycle_foreground(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeLifecycleBackground(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        nmp_app_lifecycle_background(app);
    }));
}
