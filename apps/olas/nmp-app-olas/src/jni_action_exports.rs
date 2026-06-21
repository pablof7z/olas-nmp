#![cfg(target_os = "android")]

use jni::objects::{JClass, JString};
use jni::sys::{jdouble, jint, jlong, jstring};
use jni::JNIEnv;
use nmp_ffi::{nmp_app_active_following_count, nmp_app_set_storage_path};

use crate::{
    olas_apply_follow_pack_pubkeys, olas_blossom_upload_input_json, olas_bolt11_amount_sats,
    olas_bookmark_event_action_json, olas_close_follow_pack_discovery,
    olas_decode_follow_pack_event_json, olas_location_geohash4, olas_my_invite_link,
    olas_open_follow_pack_discovery, olas_picture_post_publish_json, olas_react_action_json,
    olas_resolve_invite_json, olas_zap_action_json,
};

use super::{cstring_into_jstring, jstring_to_cstring, unpack};

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

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeReactActionJson(
    mut env: JNIEnv,
    _class: JClass,
    target_event_id: JString,
    target_author_pubkey: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(event_id) = jstring_to_cstring(&mut env, &target_event_id) else {
            return std::ptr::null_mut();
        };
        let author = jstring_to_cstring(&mut env, &target_author_pubkey);
        let author_ptr = author.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let raw = olas_react_action_json(event_id.as_ptr(), author_ptr);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeZapActionJson(
    mut env: JNIEnv,
    _class: JClass,
    recipient_pubkey: JString,
    target_event_id: JString,
    amount_msats: jlong,
    comment: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(recipient) = jstring_to_cstring(&mut env, &recipient_pubkey) else {
            return std::ptr::null_mut();
        };
        let target = jstring_to_cstring(&mut env, &target_event_id);
        let comment = jstring_to_cstring(&mut env, &comment);
        let target_ptr = target.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let comment_ptr = comment.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let amount = if amount_msats > 0 {
            amount_msats as u64
        } else {
            0
        };
        let raw = olas_zap_action_json(recipient.as_ptr(), target_ptr, amount, comment_ptr);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBolt11AmountSats(
    mut env: JNIEnv,
    _class: JClass,
    bolt11: JString,
) -> jlong {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(invoice) = jstring_to_cstring(&mut env, &bolt11) else {
            return 0_i64;
        };
        let amount = olas_bolt11_amount_sats(invoice.as_ptr());
        amount.min(i64::MAX as u64) as i64
    }));
    result.unwrap_or(0) as jlong
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeBookmarkEventActionJson(
    mut env: JNIEnv,
    _class: JClass,
    account_pubkey: JString,
    event_id: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(account) = jstring_to_cstring(&mut env, &account_pubkey) else {
            return std::ptr::null_mut();
        };
        let Some(event_id) = jstring_to_cstring(&mut env, &event_id) else {
            return std::ptr::null_mut();
        };
        let raw = olas_bookmark_event_action_json(account.as_ptr(), event_id.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeLocationGeohash4(
    mut env: JNIEnv,
    _class: JClass,
    latitude: jdouble,
    longitude: jdouble,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let raw = olas_location_geohash4(latitude as f64, longitude as f64);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// P0-B: multi-image publish. Accepts a JSON array of uploaded-image
/// descriptors (each `{"descriptor":{...BUD-02...},"alt":"...","dim":"WxH"}`)
/// and emits a single kind:20 with multiple NIP-68 `imeta` tags.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativePicturePostPublishJson(
    mut env: JNIEnv,
    _class: JClass,
    uploaded_images_json: JString,
    caption: JString,
    geohash: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(images) = jstring_to_cstring(&mut env, &uploaded_images_json) else {
            return std::ptr::null_mut();
        };
        let caption_c = jstring_to_cstring(&mut env, &caption);
        let geohash_c = jstring_to_cstring(&mut env, &geohash);
        let caption_ptr = caption_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let geohash_ptr = geohash_c.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let raw = olas_picture_post_publish_json(images.as_ptr(), caption_ptr, geohash_ptr);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// P0-A: open follow-pack discovery interest (kind:30000).
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeOpenFollowPackDiscovery(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    consumer_id: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let consumer = jstring_to_cstring(&mut env, &consumer_id);
        let consumer_ptr = consumer.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        olas_open_follow_pack_discovery(app, consumer_ptr);
    }));
}

/// P0-A: close follow-pack discovery interest.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeCloseFollowPackDiscovery(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    consumer_id: JString,
) {
    if handle == 0 {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let consumer = jstring_to_cstring(&mut env, &consumer_id);
        let consumer_ptr = consumer.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        olas_close_follow_pack_discovery(app, consumer_ptr);
    }));
}

/// P0-A: decode a kind:30000 event JSON into a FollowPack descriptor JSON.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeDecodeFollowPackEventJson(
    mut env: JNIEnv,
    _class: JClass,
    event_json: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(json) = jstring_to_cstring(&mut env, &event_json) else {
            return std::ptr::null_mut();
        };
        let raw = olas_decode_follow_pack_event_json(json.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// P0-A: apply selected follow packs by dispatching nmp.follow per pubkey.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeApplyFollowPackPubkeys(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pubkeys_json: JString,
    active_pubkey: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        let Some(pubkeys) = jstring_to_cstring(&mut env, &pubkeys_json) else {
            return std::ptr::null_mut();
        };
        let active = jstring_to_cstring(&mut env, &active_pubkey);
        let active_ptr = active.as_ref().map_or(std::ptr::null(), |c| c.as_ptr());
        let raw = olas_apply_follow_pack_pubkeys(app, pubkeys.as_ptr(), active_ptr);
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// P0-A: live "Following" count for the profile header — the number of
/// distinct `p` tags in the active account's current kind:3 in the local event
/// store (read-your-writes; reflects a just-applied follow pack with no relay
/// round-trip). Returns -1 when unavailable (no active account / not started);
/// the host renders -1 as 0.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeActiveFollowingCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    if handle == 0 {
        return -1;
    }
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let (app, _) = unpack(handle);
        nmp_app_active_following_count(app)
    }));
    result.unwrap_or(-1)
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

// ── P2-A: Invite link resolution ─────────────────────────────────────────────

/// Resolve an invite token (full URL, bare npub, or hex pubkey) to inviter info.
/// Returns `{"inviter_pubkey":"<hex>","display_hint":"npub1..."}` or null.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeResolveInviteJson(
    mut env: JNIEnv,
    _class: JClass,
    token: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(token_c) = jstring_to_cstring(&mut env, &token) else {
            return std::ptr::null_mut();
        };
        let raw = olas_resolve_invite_json(token_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}

// ── P2-C: Invite link minting ─────────────────────────────────────────────────

/// Mint the canonical invite link for the active user from their hex pubkey.
/// Returns `"https://olas.app/i/<npub>"` or null.
#[no_mangle]
pub extern "system" fn Java_io_f7z_olas_core_NMPBridge_nativeMyInviteLink(
    mut env: JNIEnv,
    _class: JClass,
    active_pubkey: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
        let Some(pk_c) = jstring_to_cstring(&mut env, &active_pubkey) else {
            return std::ptr::null_mut();
        };
        let raw = olas_my_invite_link(pk_c.as_ptr());
        cstring_into_jstring(&mut env, raw)
    }));
    result.unwrap_or(std::ptr::null_mut())
}
