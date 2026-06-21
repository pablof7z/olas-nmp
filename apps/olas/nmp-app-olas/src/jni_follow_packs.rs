// jni_follow_packs.rs — JNI shims for the follow-pack discovery step.
// Wired into jni.rs via #[path]; gated to android targets.
#![cfg(target_os = "android")]

use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;

use crate::{
    olas_apply_selected_follow_packs, olas_close_follow_pack_discovery,
    olas_follow_packs_snapshot_json, olas_open_follow_pack_discovery,
};

use super::{cstring_into_jstring, jstring_to_cstring, unpack};

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeOpenFollowPackDiscovery(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        olas_open_follow_pack_discovery(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeCloseFollowPackDiscovery(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        olas_close_follow_pack_discovery(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeFollowPacksSnapshotJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let raw = olas_follow_packs_snapshot_json(app);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeApplySelectedFollowPacks(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    ids_json: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(ids) = jstring_to_cstring(&mut env, &ids_json) else {
            return std::ptr::null_mut();
        };
        let raw = olas_apply_selected_follow_packs(app, ids.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}
