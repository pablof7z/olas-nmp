use std::ffi::CString;
use std::path::Path;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Duration;

use super::picture_feed_acquisition::search_acquisition_filter_jsons;
use super::picture_feed_test_support::*;
use super::*;

static APP_FEED_TEST_LOCK: Mutex<()> = Mutex::new(());

#[test]
fn acquisition_filter_derives_kind16_from_primary_picture_kind() {
    let filters = acquisition_filter_jsons(50);
    let values = filters
        .iter()
        .map(|filter| serde_json::from_str::<serde_json::Value>(filter).expect("json"))
        .collect::<Vec<_>>();
    assert_eq!(values[0]["kinds"], serde_json::json!([20]));
    assert_eq!(values[0]["limit"], 50);
    assert_eq!(values[1]["kinds"], serde_json::json!([16]));
}

#[test]
fn following_live_acquisition_declares_primary_picture_kind_only() {
    let primary_kinds = following_primary_kinds();
    assert_eq!(primary_kinds, [nmp_nip68::KIND_PICTURE_EVENT]);
    assert_eq!(primary_kinds, [20]);
    assert_ne!(
        primary_kinds[0], 16,
        "Olas declares kind:20 as the primary feed kind; NMP derives kind:16 acquisition"
    );
    assert!(
        nmp_nip68::picture_acquisition_kinds().contains(&16),
        "NIP-68/NIP-18 acquisition still includes kind:16 wrappers for picture reposts"
    );
}

#[test]
fn native_surfaces_do_not_expose_raw_kind20_decoder() {
    let manifest_dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    let repo_root = manifest_dir
        .ancestors()
        .nth(3)
        .expect("nmp-app-olas lives under apps/olas");
    let surfaces = [
        "apps/olas/nmp-app-olas/src/event_models.rs",
        "apps/olas/nmp-app-olas/src/extras_ffi.rs",
        "apps/ios/Olas/Bridge/olas_app.h",
        "apps/ios/Olas/Core/NMPBridge.swift",
        "apps/android/app/src/main/kotlin/io/f7z/olas/core/NMPBridge.kt",
    ];
    let forbidden = [
        "olas_decode_kind20_event_json",
        "decode_kind20",
        "decodeKind20",
        "Kind20Event",
        "kind20Event",
    ];

    for surface in surfaces {
        let contents = std::fs::read_to_string(repo_root.join(surface))
            .unwrap_or_else(|err| panic!("read {surface}: {err}"));
        for symbol in forbidden {
            assert!(
                !contents.contains(symbol),
                "{surface} must not expose raw kind:20 decoding via {symbol}; native code consumes Rust-owned photo-feed projections"
            );
        }
    }
}

#[test]
fn following_open_declares_active_follows_picture_subscription() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let frames = install_frame_capture();
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    nmp_ffi::nmp_app_set_update_callback(app, std::ptr::null_mut(), Some(capture_frame_callback));
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let active_pubkey = sign_in_test_account_and_wait(app, &frames, Duration::from_secs(5));
    let following = CString::new(FOLLOWING_FEED_KEY).expect("following key");
    crate::olas_open_photo_feed(app, 1, following.as_ptr());

    let filter = wait_for_active_follows_picture_filter(
        &frames,
        active_pubkey.as_str(),
        Duration::from_secs(5),
    )
    .expect("following feed should declare an active-follows kind:20 subscription");

    assert_eq!(filter["authors"], serde_json::json!([active_pubkey]));
    assert!(filter["kinds"]
        .as_array()
        .expect("kinds array")
        .iter()
        .any(|kind| kind.as_u64() == Some(20)));
    assert!(
        filter["kinds"]
            .as_array()
            .expect("kinds array")
            .iter()
            .any(|kind| kind.as_u64() == Some(16)),
        "NMP derives kind:16 acquisition for Olas kind:20 feeds"
    );
    assert!(
        !filter["kinds"]
            .as_array()
            .expect("kinds array")
            .iter()
            .any(|kind| kind.as_u64() == Some(6)),
        "kind:6 is only for kind:1 repost acquisition"
    );

    nmp_ffi::nmp_app_set_update_callback(app, std::ptr::null_mut(), None);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}

#[test]
fn following_feed_observer_hydrates_typed_projection_after_ingest() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let frames = install_frame_capture();
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    nmp_ffi::nmp_app_set_update_callback(app, std::ptr::null_mut(), Some(capture_frame_callback));
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let _active_pubkey = sign_in_test_account_and_wait(app, &frames, Duration::from_secs(5));
    let signal = Arc::new(EventSignal::default());
    let observer_id = unsafe { &*app }.register_event_observer(signal.clone());
    let following = CString::new(FOLLOWING_FEED_KEY).expect("following key");

    crate::olas_open_photo_feed(app, 1, following.as_ptr());
    let (event_id, event_json) = signed_test_account_picture_event(1_700_000_500);
    inject_signed_event(app, &event_json);
    let (_observed, posts_json) = signal
        .wait_for_non_empty_projection(Duration::from_secs(5), || {
            non_empty_current_photo_feed_json(app, FOLLOWING_FEED_KEY)
        });
    assert_posts_contain(&posts_json, &event_id);

    unsafe { &*app }.unregister_event_observer(observer_id);
    nmp_ffi::nmp_app_set_update_callback(app, std::ptr::null_mut(), None);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}

#[test]
fn author_acquisition_filter_derives_kind16_and_keeps_author_perspective() {
    let filters = author_acquisition_filter_jsons("alice", 50);
    let values = filters
        .iter()
        .map(|filter| serde_json::from_str::<serde_json::Value>(filter).expect("json"))
        .collect::<Vec<_>>();
    assert_eq!(values[0]["kinds"], serde_json::json!([20]));
    assert_eq!(values[1]["kinds"], serde_json::json!([16]));
    for value in values {
        assert_eq!(value["authors"], serde_json::json!(["alice"]));
        assert_eq!(value["limit"], 50);
    }
}

#[test]
fn search_acquisition_filter_derives_kind16_and_keeps_search_perspective() {
    let filters = search_acquisition_filter_jsons("nostr photos", 50);
    let values = filters
        .iter()
        .map(|filter| serde_json::from_str::<serde_json::Value>(filter).expect("json"))
        .collect::<Vec<_>>();
    assert_eq!(values[0]["kinds"], serde_json::json!([20]));
    assert_eq!(values[1]["kinds"], serde_json::json!([16]));
    for value in values {
        assert_eq!(value["search"], "nostr photos");
        assert_eq!(value["limit"], 50);
        assert!(value.get("authors").is_none());
    }
}

#[test]
fn author_shape_uses_picture_acquisition_kinds_for_single_source_author() {
    let shape = author_shape("alice").expect("shape");
    assert_eq!(shape.kinds, [16, 20].into_iter().collect());
    assert_eq!(shape.authors, ["alice".to_string()].into_iter().collect());
}

#[test]
fn network_shape_has_no_author_filter() {
    let shape = network_shape().expect("shape");
    assert_eq!(shape.kinds, [16, 20].into_iter().collect());
    assert!(shape.authors.is_empty());
}

#[test]
fn search_shape_has_no_author_filter_and_carries_relay_search() {
    let shape = search_shape("nostr photos").expect("shape");
    assert_eq!(shape.kinds, [16, 20].into_iter().collect());
    assert!(shape.authors.is_empty());
    assert_eq!(shape.search.as_deref(), Some("nostr photos"));
    assert_eq!(shape.limit, Some(50));
}

#[test]
fn open_network_preset_allows_without_wot_runtime() {
    assert!(network_allows_for_preset(&None, true, "pubkey", "open"));
}

#[test]
fn balanced_network_without_active_user_uses_relay_perspective() {
    assert!(network_allows_for_preset(
        &None, false, "pubkey", "balanced"
    ));
}

#[test]
fn balanced_network_with_active_user_rejects_without_wot_runtime() {
    assert!(!network_allows_for_preset(
        &None, true, "pubkey", "balanced"
    ));
}

#[test]
fn balanced_network_with_active_user_allows_unknown_author_by_default_policy() {
    let viewer = author(90);
    let candidate = author(91);
    let fixture = active_wot_runtime(&viewer);
    let wot = Some(fixture.runtime.clone());

    assert!(network_allows_for_preset(
        &wot, true, &candidate, "balanced"
    ));
}

#[test]
fn close_network_with_active_user_hides_unknown_author_by_threshold() {
    let viewer = author(92);
    let candidate = author(93);
    let fixture = active_wot_runtime(&viewer);
    let wot = Some(fixture.runtime.clone());

    assert!(!network_allows_for_preset(&wot, true, &candidate, "close"));
}

#[test]
fn close_network_with_active_user_allows_direct_follow() {
    let viewer = author(94);
    let followed = author(95);
    let fixture = active_wot_runtime(&viewer);
    let runtime = fixture.runtime.clone();
    runtime.on_kernel_event(&contact_list(&viewer, &[&followed]));
    let wot = Some(runtime);

    assert!(network_allows_for_preset(&wot, true, &followed, "close"));
}

#[test]
fn snapshot_decoder_returns_canonical_picture_records() {
    let feed = PictureFeed::new(Arc::new(|_| true));
    feed.on_kernel_event(&picture("picture-1", "author-1", 42));

    let payload = serde_json::to_vec(&feed.snapshot_current_window()).expect("payload");
    let posts_json = photo_feed_posts_json_from_payload(&payload).expect("posts");
    let posts: serde_json::Value = serde_json::from_str(&posts_json).expect("json");

    assert_eq!(posts[0]["event_id"], "picture-1");
    assert_eq!(posts[0]["author"], "author-1");
    assert_eq!(posts[0]["created_at"], 42);
    assert_eq!(posts[0]["content"], "caption");
    assert_eq!(
        posts[0]["images"][0]["url"],
        "https://cdn.example/picture.jpg"
    );
    assert_eq!(posts[0]["images"][0]["dimensions"]["width"], 800);
    assert_eq!(posts[0]["images"][0]["dimensions"]["height"], 600);
    assert!(posts[0].get("authorPubkey").is_none());
    assert!(posts[0].get("caption").is_none());
}

#[test]
fn snapshot_decoder_preserves_repost_attribution() {
    let feed = PictureFeed::new(Arc::new(|_| true));
    let target = picture("picture-1", "target-author", 42);
    let repost = repost("repost-1", "reposter", &target, 99);

    feed.on_kernel_event(&repost);
    let payload = serde_json::to_vec(&feed.snapshot_current_window()).expect("payload");
    let posts_json = photo_feed_posts_json_from_payload(&payload).expect("posts");
    let posts: serde_json::Value = serde_json::from_str(&posts_json).expect("json");

    assert_eq!(posts[0]["event_id"], "picture-1");
    assert_eq!(posts[0]["author"], "target-author");
    assert_eq!(posts[0]["repostedBy"]["authorPubkey"], "reposter");
    assert_eq!(posts[0]["repostedBy"]["repostEventId"], "repost-1");
    assert_eq!(posts[0]["repostedBy"]["repostCreatedAt"], 99);
}

#[test]
fn current_photo_feed_json_replays_loaded_network_projection() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let signal = Arc::new(EventSignal::default());
    let observer_id = unsafe { &*app }.register_event_observer(signal.clone());
    let network = CString::new(NETWORK_FEED_KEY).expect("network key");

    crate::olas_open_photo_feed(app, 0, network.as_ptr());
    let (event_id, event_json) = signed_picture_event(1_700_000_200);
    inject_signed_event(app, &event_json);
    let (_observed, pushed_posts_json) = signal
        .wait_for_non_empty_projection(Duration::from_secs(5), || {
            non_empty_network_posts_json(app)
        });
    assert_posts_contain(&pushed_posts_json, &event_id);

    let current_posts_json = current_photo_feed_json_via_ffi(app, NETWORK_FEED_KEY)
        .expect("current photo feed projection");
    assert_posts_contain(&current_posts_json, &event_id);

    unsafe { &*app }.unregister_event_observer(observer_id);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}

#[test]
fn repeated_network_open_preserves_loaded_projection() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let signal = Arc::new(EventSignal::default());
    let observer_id = unsafe { &*app }.register_event_observer(signal.clone());
    let network = CString::new(NETWORK_FEED_KEY).expect("network key");

    crate::olas_open_photo_feed(app, 0, network.as_ptr());
    let (event_id, event_json) = signed_picture_event(1_700_000_300);
    inject_signed_event(app, &event_json);
    let (_observed, initial_posts_json) = signal
        .wait_for_non_empty_projection(Duration::from_secs(5), || {
            non_empty_network_posts_json(app)
        });
    assert_posts_contain(&initial_posts_json, &event_id);

    crate::olas_open_photo_feed(app, 0, network.as_ptr());
    let current_posts_json = current_photo_feed_json_via_ffi(app, NETWORK_FEED_KEY)
        .expect("current projection survives repeated open");
    assert_posts_contain(&current_posts_json, &event_id);

    unsafe { &*app }.unregister_event_observer(observer_id);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}

#[test]
fn network_feed_reopens_from_store_after_perspective_switch() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let signal = Arc::new(EventSignal::default());
    let observer_id = unsafe { &*app }.register_event_observer(signal.clone());
    let network = CString::new(NETWORK_FEED_KEY).expect("network key");
    let following = CString::new(FOLLOWING_FEED_KEY).expect("following key");

    crate::olas_open_photo_feed(app, 0, network.as_ptr());
    let (event_id, event_json) = signed_picture_event(1_700_000_100);
    inject_signed_event(app, &event_json);
    let (_observed, initial_posts_json) = signal
        .wait_for_non_empty_projection(Duration::from_secs(5), || {
            non_empty_network_posts_json(app)
        });
    assert_posts_contain(&initial_posts_json, &event_id);

    crate::olas_open_photo_feed(app, 1, following.as_ptr());
    crate::olas_open_photo_feed(app, 0, network.as_ptr());
    let reopened_posts_json = non_empty_network_posts_json(app)
        .expect("network feed rehydrates from the kernel store on reopen");
    assert_posts_contain(&reopened_posts_json, &event_id);

    unsafe { &*app }.unregister_event_observer(observer_id);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}

#[test]
fn search_feed_open_hydrates_typed_picture_projection() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let signal = Arc::new(EventSignal::default());
    let observer_id = unsafe { &*app }.register_event_observer(signal.clone());
    let query = CString::new("caption").expect("query");
    let consumer = CString::new(SEARCH_FEED_KEY).expect("search key");

    crate::olas_open_search_feed(app, query.as_ptr(), consumer.as_ptr());
    let (event_id, event_json) = signed_picture_event(1_700_000_400);
    inject_signed_event(app, &event_json);
    let (_observed, posts_json) =
        signal.wait_for_non_empty_projection(Duration::from_secs(5), || {
            current_photo_feed_json_via_ffi(app, SEARCH_FEED_KEY).filter(|json| {
                serde_json::from_str::<serde_json::Value>(json)
                    .ok()
                    .and_then(|value| value.as_array().map(|rows| !rows.is_empty()))
                    .unwrap_or(false)
            })
        });
    assert_posts_contain(&posts_json, &event_id);

    crate::olas_close_search_feed(app, query.as_ptr(), consumer.as_ptr());
    assert!(
        current_photo_feed_json_via_ffi(app, SEARCH_FEED_KEY).is_none(),
        "closing search must unregister the typed search projection"
    );

    unsafe { &*app }.unregister_event_observer(observer_id);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}

#[test]
#[ignore = "real relay smoke for NMP #1626; run explicitly when validating Olas feed acquisition"]
fn real_relay_network_feed_hydrates_kind20_projection() {
    let _guard = APP_FEED_TEST_LOCK.lock().expect("feed test lock");
    let frames = install_frame_capture();
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    nmp_ffi::nmp_app_set_update_callback(app, std::ptr::null_mut(), Some(capture_frame_callback));
    crate::olas_app_register(app);
    nmp_ffi::nmp_app_consume_all_builtin_projections(app);
    nmp_ffi::nmp_app_start(app, 0, 100);

    let signal = Arc::new(EventSignal::default());
    let observer_id = unsafe { &*app }.register_event_observer(signal.clone());
    let consumer = std::ffi::CString::new(NETWORK_FEED_KEY).expect("static key");
    crate::olas_open_photo_feed(app, 0, consumer.as_ptr());

    let posts_json = wait_for_non_empty_network_frame(&frames, Duration::from_secs(45), &signal)
        .expect("real relay projection hydrates kind:20 posts");
    assert_posts_array_non_empty(&posts_json);

    unsafe { &*app }.unregister_event_observer(observer_id);
    nmp_ffi::nmp_app_set_update_callback(app, std::ptr::null_mut(), None);
    nmp_ffi::nmp_app_stop(app);
    nmp_ffi::nmp_app_free(app);
}
