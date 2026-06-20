#![cfg(target_os = "android")]

use std::ffi::{CStr, CString};
use std::sync::{Arc, Mutex};

use jni::objects::{GlobalRef, JClass, JObject, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
use nmp_ffi::{
    nmp_app_add_relay, nmp_app_consume_all_builtin_projections, nmp_app_free, nmp_app_new,
    nmp_app_set_update_callback, nmp_app_signin_nsec, nmp_app_start, nmp_app_stop, nmp_free_string,
    NmpApp,
};

use crate::{olas_app_register, olas_create_account, olas_seed_default_relays};

#[path = "jni_action_exports.rs"]
mod action_exports;
#[path = "jni_event_models.rs"]
mod event_model_exports;
#[path = "jni_photo_feed.rs"]
mod photo_feed_exports;
#[path = "jni_runtime_exports.rs"]
mod runtime_exports;

struct UpdatePushListener {
    vm: jni::JavaVM,
    handler: GlobalRef,
}

unsafe impl Send for UpdatePushListener {}
unsafe impl Sync for UpdatePushListener {}

impl UpdatePushListener {
    fn new(vm: jni::JavaVM, handler: GlobalRef) -> Self {
        Self { vm, handler }
    }

    fn push(&self, bytes: &[u8]) {
        let mut env = match self.vm.attach_current_thread() {
            Ok(env) => env,
            Err(_) => return,
        };
        let _ = env.with_local_frame(8, |env| -> Result<(), jni::errors::Error> {
            let array = env.byte_array_from_slice(bytes)?;
            env.call_method(
                &self.handler,
                "onUpdate",
                "([B)V",
                &[jni::objects::JValueGen::Object(array.as_ref())],
            )?;
            Ok(())
        });
        let _ = env.exception_clear();
    }
}

struct CallbackCtx {
    listener: Mutex<Option<Arc<UpdatePushListener>>>,
}

type KernelEventObserverFn = extern "C" fn(*mut std::ffi::c_void, *const std::os::raw::c_char);

struct EventObserverCtx {
    vm: jni::JavaVM,
    handler: GlobalRef,
}

unsafe impl Send for EventObserverCtx {}
unsafe impl Sync for EventObserverCtx {}

extern "C" fn on_event(context: *mut std::ffi::c_void, event_json: *const std::os::raw::c_char) {
    if context.is_null() || event_json.is_null() {
        return;
    }
    let ctx = unsafe { &*(context as *const EventObserverCtx) };
    let json = match unsafe { CStr::from_ptr(event_json) }.to_str() {
        Ok(value) => value.to_owned(),
        Err(_) => return,
    };
    let mut env = match ctx.vm.attach_current_thread() {
        Ok(env) => env,
        Err(_) => return,
    };
    let _ = env.with_local_frame(8, |env| -> Result<(), jni::errors::Error> {
        let jstr = env.new_string(&json)?;
        env.call_method(
            &ctx.handler,
            "onEvent",
            "(Ljava/lang/String;)V",
            &[jni::objects::JValueGen::Object(jstr.as_ref())],
        )?;
        Ok(())
    });
    let _ = env.exception_clear();
}

extern "C" fn on_update(context: *mut std::ffi::c_void, bytes: *const u8, len: usize) {
    if context.is_null() || bytes.is_null() {
        return;
    }
    let ctx = unsafe { &*(context as *const CallbackCtx) };
    let frame = unsafe { std::slice::from_raw_parts(bytes, len) };
    let listener = ctx.listener.lock().ok().and_then(|guard| guard.clone());
    if let Some(listener) = listener {
        listener.push(frame);
    }
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
    let value = CStr::from_ptr(raw).to_string_lossy().into_owned();
    nmp_free_string(raw);
    match env.new_string(&value) {
        Ok(jstring) => jstring.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let app = nmp_app_new();
    if app.is_null() {
        return 0;
    }
    let ctx = Arc::new(CallbackCtx {
        listener: Mutex::new(None),
    });
    let ctx_ptr = Arc::into_raw(ctx) as *mut std::ffi::c_void;
    nmp_app_set_update_callback(app, ctx_ptr, Some(on_update));
    Box::into_raw(Box::new((app as usize, ctx_ptr as usize))) as jlong
}

unsafe fn unpack(handle: jlong) -> (*mut NmpApp, *mut std::ffi::c_void) {
    let pair = &*(handle as *const (usize, usize));
    (pair.0 as *mut NmpApp, pair.1 as *mut std::ffi::c_void)
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, ctx_ptr) = unpack(handle);
        nmp_app_set_update_callback(app, std::ptr::null_mut(), None);
        drop(Arc::from_raw(ctx_ptr as *const CallbackCtx));
        nmp_app_free(app);
        drop(Box::from_raw(handle as *mut (usize, usize)));
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeRegister(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        olas_app_register(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeConsumeAllBuiltinProjections(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        nmp_app_consume_all_builtin_projections(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeSeedDefaultRelays(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        olas_seed_default_relays(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    storage_path: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let _ = jstring_to_cstring(&mut env, &storage_path);
        nmp_app_start(app, 0, 50, 4);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeStop(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        nmp_app_stop(app);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeSetUpdateListener(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    listener: JObject,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (_, ctx_ptr) = unpack(handle);
        let ctx = &*(ctx_ptr as *const CallbackCtx);
        if listener.is_null() {
            if let Ok(mut slot) = ctx.listener.lock() {
                slot.take();
            }
            return;
        }
        let Ok(vm) = env.get_java_vm() else { return };
        let Ok(global) = env.new_global_ref(&listener) else {
            return;
        };
        if let Ok(mut slot) = ctx.listener.lock() {
            *slot = Some(Arc::new(UpdatePushListener::new(vm, global)));
        }
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeCreateAccount(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    name: JString,
    username: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(name) = jstring_to_cstring(&mut env, &name) else {
            return;
        };
        let Some(username) = jstring_to_cstring(&mut env, &username) else {
            return;
        };
        olas_create_account(app, name.as_ptr(), username.as_ptr());
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeSignInNsec(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    nsec: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(secret) = jstring_to_cstring(&mut env, &nsec) else {
            return;
        };
        nmp_app_signin_nsec(app, secret.as_ptr(), 1);
    }));
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeAddRelay(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
    role: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(url) = jstring_to_cstring(&mut env, &url) else {
            return;
        };
        let Some(role) = jstring_to_cstring(&mut env, &role) else {
            return;
        };
        nmp_app_add_relay(app, url.as_ptr(), role.as_ptr());
    }));
}
