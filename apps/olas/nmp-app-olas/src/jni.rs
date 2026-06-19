// jni.rs — Android JNI shim for nmp-app-olas.
//
// Doctrine: no business logic here — pure transport from Kotlin to nmp-ffi
// and olas-specific FFI symbols. Errors never cross the JNI seam; the kernel
// reports via update frames. Update delivery is push-only (D8: no polling) via
// a Kotlin listener stored as a GlobalRef, invoked on attach_current_thread.
//
// JNI class: io.f7z.olas.core.NMPBridge (static object)
// Native library: nmp_app_olas (loaded via System.loadLibrary)

#![cfg(target_os = "android")]

use std::ffi::{CStr, CString};
use std::sync::{Arc, Mutex};

use jni::objects::{GlobalRef, JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;

use nmp_ffi::{
    nmp_app_add_relay, nmp_app_claim_profile, nmp_app_consume_all_builtin_projections,
    nmp_app_create_new_account, nmp_app_dispatch_action, nmp_app_free,
    nmp_app_lifecycle_background, nmp_app_lifecycle_foreground, nmp_app_load_older_feed,
    nmp_app_new, nmp_app_open_contact_feed, nmp_app_register_event_observer,
    nmp_app_release_profile, nmp_app_set_storage_path, nmp_app_set_update_callback,
    nmp_app_signin_nsec, nmp_app_start, nmp_app_stop, nmp_app_wallet_connect, nmp_free_string,
    NmpApp,
};

use crate::{
    olas_app_register, olas_blossom_upload_input_json, olas_close_search_feed,
    olas_create_account, olas_open_photo_feed, olas_open_search_feed, olas_picture_post_publish_json,
    olas_seed_default_relays,
};

// ---------------------------------------------------------------------------
// Push listener — mirrors nmp-android-ffi UpdatePushListener
// ---------------------------------------------------------------------------

struct UpdatePushListener {
    vm: jni::JavaVM,
    handler: GlobalRef,
}

// SAFETY: JavaVM is Send+Sync; GlobalRef is tracked by the JVM.
unsafe impl Send for UpdatePushListener {}
unsafe impl Sync for UpdatePushListener {}

impl UpdatePushListener {
    fn new(vm: jni::JavaVM, handler: GlobalRef) -> Self {
        Self { vm, handler }
    }

    fn push(&self, bytes: &[u8]) {
        let mut env = match self.vm.attach_current_thread() {
            Ok(e) => e,
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

// ---------------------------------------------------------------------------
// Callback context stored as a raw pointer baked into the C callback
// ---------------------------------------------------------------------------

struct CallbackCtx {
    listener: Mutex<Option<Arc<UpdatePushListener>>>,
}

// KernelEventObserverFn = extern "C" fn(*mut c_void, *const c_char) — 2 params only.
type KernelEventObserverFn = extern "C" fn(*mut std::ffi::c_void, *const std::os::raw::c_char);

// ---------------------------------------------------------------------------
// Event observer context — delivers JSON-encoded KernelEvent strings to Kotlin
// ---------------------------------------------------------------------------

struct EventObserverCtx {
    vm: jni::JavaVM,
    handler: GlobalRef,
}

// SAFETY: JavaVM is Send+Sync; GlobalRef is tracked by the JVM.
unsafe impl Send for EventObserverCtx {}
unsafe impl Sync for EventObserverCtx {}

extern "C" fn on_event(context: *mut std::ffi::c_void, event_json: *const std::os::raw::c_char) {
    if context.is_null() || event_json.is_null() {
        return;
    }
    // SAFETY: context was cast from Box<EventObserverCtx> via into_raw; the Box
    // keeps it alive until nativeFree.
    let ctx = unsafe { &*(context as *const EventObserverCtx) };
    let json_str = match unsafe { std::ffi::CStr::from_ptr(event_json) }.to_str() {
        Ok(s) => s.to_owned(),
        Err(_) => return,
    };
    let mut env = match ctx.vm.attach_current_thread() {
        Ok(e) => e,
        Err(_) => return,
    };
    let _ = env.with_local_frame(8, |env| -> Result<(), jni::errors::Error> {
        let jstr = env.new_string(&json_str)?;
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
    // SAFETY: context was cast from Arc<CallbackCtx> via into_raw; the Arc
    // keeps it alive until nativeFree clears the callback.
    let ctx = unsafe { &*(context as *const CallbackCtx) };
    let frame = unsafe { std::slice::from_raw_parts(bytes, len) };
    // Snapshot Arc under lock, drop lock before the JNI upcall (deadlock prevention).
    let snapshot: Option<Arc<UpdatePushListener>> =
        ctx.listener.lock().ok().and_then(|g| g.clone());
    if let Some(l) = snapshot {
        l.push(frame);
    }
}

// ---------------------------------------------------------------------------
// Helper: obtain a CString from a JString argument
//
// Async action terminals (the BUD-02 blob descriptor for nmp.blossom.upload,
// the publish verdict for nmp.publish) are NOT delivered through a dedicated
// observer — `nmp-core` writes them into the snapshot's
// `projections.action_results` per-tick drain, which already flows to Kotlin via
// the registered event observer (the `events` SharedFlow). The Kotlin bridge
// parses that drain by correlation_id, so no extra JNI observer is needed here.
// ---------------------------------------------------------------------------

fn jstring_to_cstring(env: &mut JNIEnv, s: &JString) -> Option<CString> {
    let obj: &JObject = s.as_ref();
    if obj.is_null() {
        return None;
    }
    let java_str = env.get_string(s).ok()?;
    CString::new(java_str.to_string_lossy().as_bytes()).ok()
}

/// Convert a Rust-owned `*mut c_char` (from an `olas_*` helper) into a Java
/// string and free the C string. NULL in → NULL out.
/// SAFETY: `raw` must be NULL or a pointer returned by an `olas_*_json` helper
/// (freeable via `nmp_free_string`).
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

// ---------------------------------------------------------------------------
// JNI exports — Java_io_f7z_olas_core_NMPBridge_<method>
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let app = nmp_app_new();
    if app.is_null() {
        return 0;
    }
    // Allocate callback context; register it with the kernel update callback.
    // We store the Arc as a raw pointer in the C context slot; freed in nativeFree.
    let ctx = Arc::new(CallbackCtx {
        listener: Mutex::new(None),
    });
    let ctx_ptr = Arc::into_raw(ctx) as *mut std::ffi::c_void;
    nmp_app_set_update_callback(app, ctx_ptr, Some(on_update));
    // Pack (app, ctx_ptr) into a single heap allocation so Kotlin can carry one jlong.
    // We encode as: Box<(*mut NmpApp, *mut c_void)> → raw pointer → jlong.
    let pair = Box::new((app as usize, ctx_ptr as usize));
    Box::into_raw(pair) as jlong
}

/// Decompose the packed jlong back into (app_ptr, ctx_ptr).
/// SAFETY: caller must ensure handle != 0 and was produced by nativeNew.
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
        // Quiesce the update callback before freeing anything.
        nmp_app_set_update_callback(app, std::ptr::null_mut(), None);
        // Reclaim the CallbackCtx Arc.
        drop(Arc::from_raw(ctx_ptr as *const CallbackCtx));
        // Free the kernel.
        nmp_app_free(app);
        // Reclaim the pair box.
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

/// ADR-0053: declare projection-consumption intent before `nmp_app_start` so the
/// kernel emits the built-in projections (including `action_results`, the async
/// action terminal drain the bridge awaits). Mirrors the iOS bridge's
/// `nmp_app_consume_all_builtin_projections` call.
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
        // storage path is set separately; start with default tick params.
        let _ = jstring_to_cstring(&mut env, &storage_path); // accept but ignore (set via nativeSetStoragePath)
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
        let Ok(global) = env.new_global_ref(&listener) else { return };
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
        let Some(name_c) = jstring_to_cstring(&mut env, &name) else { return };
        let Some(user_c) = jstring_to_cstring(&mut env, &username) else { return };
        olas_create_account(app, name_c.as_ptr(), user_c.as_ptr());
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
        let Some(secret) = jstring_to_cstring(&mut env, &nsec) else { return };
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
        let Some(url_c) = jstring_to_cstring(&mut env, &url) else { return };
        let Some(role_c) = jstring_to_cstring(&mut env, &role) else { return };
        nmp_app_add_relay(app, url_c.as_ptr(), role_c.as_ptr());
    }));
}

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
        let Some(kinds) = jstring_to_cstring(&mut env, &kinds_json) else { return };
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
        let consumer = CString::new("olas.photo_feed").unwrap();
        olas_open_photo_feed(app, if contact_list_only != 0 { 1 } else { 0 }, consumer.as_ptr());
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
        let result_str = CStr::from_ptr(raw).to_string_lossy().into_owned();
        nmp_free_string(raw);
        match env.new_string(&result_str) {
            Ok(s) => s.into_raw(),
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
        let Ok(global) = env.new_global_ref(&listener) else { return };
        // Heap-allocate the observer context; its raw pointer is stored in the
        // kernel's observer slot and must outlive the app.  It is intentionally
        // leaked here — the kernel will call on_event with it for the app's
        // lifetime.  A production implementation would store the returned id
        // and call nmp_app_unregister_event_observer in nativeFree.
        let ctx = Box::new(EventObserverCtx { vm, handler: global });
        let ctx_ptr = Box::into_raw(ctx) as *mut std::ffi::c_void;
        nmp_app_register_event_observer(app, ctx_ptr, Some(on_event as KernelEventObserverFn));
    }));
}

/// Decode async action terminals from a FlatBuffer snapshot frame (delivered by
/// the update callback) into a JSON array string.  Returns null if the frame
/// carries no action results.  Callers must not free the returned jstring — it
/// is a local JNI reference managed by the JVM.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeActionResults(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    frame: JByteArray,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let bytes: Vec<u8> = match env.convert_byte_array(&frame) {
            Ok(b) => b,
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

/// Tell the kernel to fetch and maintain the kind:0 profile for `pubkey`.
/// `force = 1` ensures a fresh fetch even if the profile is cached.
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
        let Some(pk_c) = jstring_to_cstring(&mut env, &pubkey) else { return };
        let Some(cid_c) = jstring_to_cstring(&mut env, &consumer_id) else { return };
        nmp_app_claim_profile(app, pk_c.as_ptr(), cid_c.as_ptr(), 0, 0);
    }));
}

/// Release a previously claimed profile so the kernel can stop tracking it.
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
        let Some(pk_c) = jstring_to_cstring(&mut env, &pubkey) else { return };
        let Some(cid_c) = jstring_to_cstring(&mut env, &consumer_id) else { return };
        nmp_app_release_profile(app, pk_c.as_ptr(), cid_c.as_ptr());
    }));
}

/// Decode the `claimed_profiles` projection from a snapshot FlatBuffer frame.
/// Returns a JSON string or null if the frame carries no profiles.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeClaimedProfiles(
    mut env: JNIEnv,
    _class: JClass,
    _handle: jlong,
    frame: JByteArray,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let bytes: Vec<u8> = match env.convert_byte_array(&frame) {
            Ok(b) => b,
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
        let Some(key_c) = jstring_to_cstring(&mut env, &key) else { return };
        nmp_app_load_older_feed(app, key_c.as_ptr());
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

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeWalletConnect(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    uri: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(uri_c) = jstring_to_cstring(&mut env, &uri) else { return };
        nmp_app_wallet_connect(app, uri_c.as_ptr());
    }));
}

/// Build the `nmp.blossom.upload` action input JSON in Rust. Pure JSON-shape
/// construction (delegates to `olas_blossom_upload_input_json`); no business
/// logic on the JNI seam. A null/empty `content_type` or `server_url` lets the
/// helper apply its defaults (sniff MIME / primal.net).
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBlossomUploadInputJson(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
    content_type: JString,
    server_url: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(path) = jstring_to_cstring(&mut env, &file_path) else {
            return std::ptr::null_mut();
        };
        let mime = jstring_to_cstring(&mut env, &content_type);
        let server = jstring_to_cstring(&mut env, &server_url);
        let mime_ptr = mime.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let server_ptr = server.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let raw = olas_blossom_upload_input_json(path.as_ptr(), mime_ptr, server_ptr);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Build the `nmp.publish` action input JSON for a NIP-68 picture post in Rust.
/// Delegates to `olas_picture_post_publish_json`; the kernel's `nmp.publish`
/// action constructs and signs the kind:20 — no event JSON or signing in Kotlin.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativePicturePostPublishJson(
    mut env: JNIEnv,
    _class: JClass,
    blossom_result_json: JString,
    caption: JString,
    alt: JString,
    dim: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(descriptor) = jstring_to_cstring(&mut env, &blossom_result_json) else {
            return std::ptr::null_mut();
        };
        let caption_c = jstring_to_cstring(&mut env, &caption);
        let alt_c = jstring_to_cstring(&mut env, &alt);
        let dim_c = jstring_to_cstring(&mut env, &dim);
        let caption_ptr = caption_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let alt_ptr = alt_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let dim_ptr = dim_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let raw = olas_picture_post_publish_json(
            descriptor.as_ptr(),
            caption_ptr,
            alt_ptr,
            dim_ptr,
            std::ptr::null(), // geohash — not yet exposed in this JNI binding
        );
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeSetStoragePath(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    path: JString,
) -> jint {
    if handle == 0 {
        return -1;
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(path_c) = jstring_to_cstring(&mut env, &path) else {
            return -1_i32;
        };
        nmp_app_set_storage_path(app, path_c.as_ptr()) as i32
    }));
    result.unwrap_or(-1)
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
        let Some(query_c) = jstring_to_cstring(&mut env, &query) else { return };
        let Some(consumer_c) = jstring_to_cstring(&mut env, &consumer_id) else { return };
        olas_open_search_feed(app, query_c.as_ptr(), consumer_c.as_ptr());
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
        let Some(query_c) = jstring_to_cstring(&mut env, &query) else { return };
        let Some(consumer_c) = jstring_to_cstring(&mut env, &consumer_id) else { return };
        olas_close_search_feed(app, query_c.as_ptr(), consumer_c.as_ptr());
    }));
}
