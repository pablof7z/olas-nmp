use std::collections::BTreeSet;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::{Arc, Mutex, OnceLock};

use nmp_core::slots::{event_by_id_from_store, ActiveAccountSlot};
use nmp_core::substrate::{empty_suppression_lookup, KernelEvent, SuppressionLookup};
use nmp_core::{KernelEventObserver, TypedProjectionData};
use nmp_feed::{ClosureInterestShape, FeedAdvance, FeedApply, FeedController, PullFeedController};
use nmp_ffi::{
    nmp_app_close_contact_feed, nmp_app_close_interest, nmp_app_open_contact_feed,
    nmp_app_open_interest, NmpApp,
};
use nmp_nip02::ActiveFollowSet;
use nmp_nip68::{
    picture_acquisition_kinds, picture_feed_observer, picture_feed_predicate, PictureFeed,
};
use nmp_planner::InterestShape;
use nmp_wot::WotBootstrapRuntime;

#[path = "picture_feed_admission.rs"]
mod picture_feed_admission;
use picture_feed_admission::network_allows;
#[cfg(test)]
use picture_feed_admission::network_allows_for_preset;
#[path = "picture_feed_acquisition.rs"]
mod picture_feed_acquisition;
use picture_feed_acquisition::{
    acquisition_filter_jsons, author_acquisition_filter_jsons, author_feed_key,
    PICTURE_PRIMARY_KINDS_JSON,
};
#[path = "picture_feed_projection.rs"]
mod picture_feed_projection;
use picture_feed_projection::{current_photo_feed_json, photo_feed_posts_json_from_payload};
#[path = "picture_feed_slots.rs"]
mod picture_feed_slots;
pub(crate) use picture_feed_slots::reset_network_picture_feed;
use picture_feed_slots::{
    clear_picture_feed_slots, picture_feed_registered, record_picture_feed,
    remove_author_picture_feed, reset_all_picture_feeds, reset_home_picture_feeds,
};
const PHOTO_FEED_SCHEMA_ID: &str = "olas.picture.feed";
const PHOTO_FEED_SCHEMA_VERSION: u32 = 1;
const PHOTO_FEED_FILE_IDENTIFIER: &str = "OLPF";
pub(crate) const FOLLOWING_FEED_KEY: &str = "olas.following_feed";
pub(crate) const NETWORK_FEED_KEY: &str = "olas.network_feed";

#[derive(Clone)]
struct FeedRuntime {
    follow_set: Arc<ActiveFollowSet>,
    wot: Option<Arc<WotBootstrapRuntime>>,
    suppression: Arc<dyn SuppressionLookup>,
    active_slot: ActiveAccountSlot,
}

static FEED_RUNTIME: OnceLock<Mutex<Option<FeedRuntime>>> = OnceLock::new();

pub(crate) fn install_runtime_handles(
    app: &NmpApp,
    handles: &nmp_defaults::NmpDefaultRuntimeHandles,
) {
    let active_slot = app.active_account_handle();
    let follow_set = ActiveFollowSet::new(active_slot.clone());
    let observer: Arc<dyn KernelEventObserver> = follow_set.clone();
    let _ = app.register_event_observer(observer);

    let follow_for_identity = follow_set.clone();
    app.register_identity_change_observer(move |_| {
        follow_for_identity.notify_account_changed();
        reset_all_picture_feeds();
    });
    follow_set.on_change(Box::new(reset_home_picture_feeds));

    let suppression: Arc<dyn SuppressionLookup> = handles
        .mute
        .clone()
        .map(|mute| mute as Arc<dyn SuppressionLookup>)
        .unwrap_or_else(empty_suppression_lookup);

    if let Ok(mut slot) = FEED_RUNTIME.get_or_init(|| Mutex::new(None)).lock() {
        *slot = Some(FeedRuntime {
            follow_set,
            wot: handles.wot.clone(),
            suppression,
            active_slot,
        });
    }
    clear_picture_feed_slots();
}

#[no_mangle]
pub extern "C" fn olas_open_photo_feed(
    app: *mut NmpApp,
    contact_list_only: u8,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        let mode = FeedMode::from_contact_flag(contact_list_only);
        let consumer = c_string_opt(consumer_id).unwrap_or_else(|| mode.default_key().to_string());

        // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
        let app_ref = unsafe { &*app };
        register_picture_feed(app_ref, consumer.clone(), mode.clone());
        open_feed_acquisition(app, &consumer, &mode);
    }));
}

#[no_mangle]
pub extern "C" fn olas_open_author_photo_feed(
    app: *mut NmpApp,
    pubkey: *const c_char,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        let Some(pubkey) = c_string_opt(pubkey) else {
            return;
        };
        let consumer =
            c_string_opt(consumer_id).unwrap_or_else(|| author_feed_key(pubkey.as_str()));
        let mode = FeedMode::Author(pubkey);

        // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
        let app_ref = unsafe { &*app };
        register_picture_feed(app_ref, consumer.clone(), mode.clone());
        open_feed_acquisition(app, &consumer, &mode);
    }));
}

#[no_mangle]
pub extern "C" fn olas_close_author_photo_feed(
    app: *mut NmpApp,
    pubkey: *const c_char,
    consumer_id: *const c_char,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if app.is_null() {
            return;
        }
        let Some(pubkey) = c_string_opt(pubkey) else {
            return;
        };
        let consumer =
            c_string_opt(consumer_id).unwrap_or_else(|| author_feed_key(pubkey.as_str()));
        close_author_acquisition(app, pubkey.as_str(), consumer.as_str());

        // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
        let app_ref = unsafe { &*app };
        let _ = app_ref.unregister_feed(consumer.as_str());
        remove_author_picture_feed(consumer.as_str());
    }));
}

#[no_mangle]
pub extern "C" fn olas_decode_snapshot_photo_feed_json(
    frame: *const u8,
    len: usize,
    key: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(|| -> Option<String> {
        if frame.is_null() || len == 0 {
            return None;
        }
        let key = c_string_opt(key).unwrap_or_else(|| NETWORK_FEED_KEY.to_string());
        let bytes = unsafe { std::slice::from_raw_parts(frame, len) };
        let projections = nmp_core::decode_snapshot_typed_projections(bytes).ok()?;
        let entry = projections.iter().find(|entry| entry.key == key)?;
        photo_feed_posts_json_from_payload(&entry.payload)
    });
    result
        .ok()
        .flatten()
        .map(|value| {
            CString::new(value)
                .map(CString::into_raw)
                .unwrap_or(std::ptr::null_mut())
        })
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "C" fn olas_current_photo_feed_json(
    app: *mut NmpApp,
    key: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> Option<String> {
        if app.is_null() {
            return None;
        }
        let key = c_string_opt(key).unwrap_or_else(|| NETWORK_FEED_KEY.to_string());
        let app_ref = unsafe { &*app };
        current_photo_feed_json(app_ref, &key)
    }));
    result
        .ok()
        .flatten()
        .map(|value| {
            CString::new(value)
                .map(CString::into_raw)
                .unwrap_or(std::ptr::null_mut())
        })
        .unwrap_or(std::ptr::null_mut())
}

fn register_picture_feed(app: &NmpApp, key: String, mode: FeedMode) {
    let Some(runtime) = current_runtime() else {
        return;
    };
    if picture_feed_registered(&key, &mode) {
        return;
    }
    let event_store = app.event_store_handle();
    let event_lookup: nmp_feed::EventLookup =
        Arc::new(move |id| event_by_id_from_store(&event_store, id));

    let feed = PictureFeed::with_event_lookup(mode.predicate(&runtime), event_lookup.clone(), None);
    let observer = picture_feed_observer(feed.clone(), event_lookup, runtime.suppression.clone());

    let provider = mode.provider(runtime);
    let apply_observer = observer.clone();
    let apply: FeedApply = Arc::new(move |event: &KernelEvent| {
        KernelEventObserver::on_kernel_event(&*apply_observer, event);
    });
    let replace_feed = feed.clone();
    let replace: nmp_feed::FeedReplace = Arc::new(move |source_id| {
        replace_feed.remove_source_id(source_id);
    });
    let advance_feed = feed.clone();
    let advance: FeedAdvance = Arc::new(move || {
        advance_feed.grow_visible_window();
    });
    let controller = PullFeedController::new_with_replacement(
        provider,
        app.feed_pull_fn(),
        apply,
        replace,
        advance,
    );
    let _ = controller.load_older();
    app.register_feed_with_observer(key.clone(), controller, observer);

    let projection_feed = feed.clone();
    let projection_key = key.clone();
    app.register_typed_snapshot_projection(key.clone(), move || {
        let snapshot = projection_feed.snapshot_current_window();
        let payload = serde_json::to_vec(&snapshot).ok()?;
        Some(TypedProjectionData {
            key: projection_key.clone(),
            schema_id: PHOTO_FEED_SCHEMA_ID.to_string(),
            schema_version: PHOTO_FEED_SCHEMA_VERSION,
            file_identifier: PHOTO_FEED_FILE_IDENTIFIER.to_string(),
            payload,
            ..Default::default()
        })
    });

    record_picture_feed(&key, &mode, feed);
}

fn current_runtime() -> Option<FeedRuntime> {
    FEED_RUNTIME
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.clone()))
}

#[derive(Clone)]
enum FeedMode {
    Following,
    Network,
    Author(String),
}

impl FeedMode {
    fn from_contact_flag(contact_list_only: u8) -> Self {
        if contact_list_only != 0 {
            Self::Following
        } else {
            Self::Network
        }
    }

    fn default_key(&self) -> &'static str {
        match self {
            Self::Following => FOLLOWING_FEED_KEY,
            Self::Network => NETWORK_FEED_KEY,
            Self::Author(_) => "olas.author_feed",
        }
    }

    fn limit(&self) -> u64 {
        match self {
            Self::Following => 50,
            Self::Network => 100,
            Self::Author(_) => 50,
        }
    }

    fn predicate(&self, runtime: &FeedRuntime) -> nmp_nip68::PictureFeedPredicate {
        match self {
            Self::Following => picture_feed_predicate(runtime.follow_set.predicate()),
            Self::Network => {
                let wot = runtime.wot.clone();
                let active_slot = runtime.active_slot.clone();
                picture_feed_predicate(Arc::new(move |author| {
                    let has_active = active_slot
                        .lock()
                        .ok()
                        .and_then(|guard| guard.clone())
                        .is_some();
                    network_allows(&wot, has_active, author)
                }))
            }
            Self::Author(pubkey) => {
                let pubkey = pubkey.clone();
                picture_feed_predicate(Arc::new(move |author| author == pubkey))
            }
        }
    }

    fn provider(&self, runtime: FeedRuntime) -> Arc<dyn nmp_feed::FeedInterestShape + Send + Sync> {
        match self {
            Self::Following => Arc::new(ClosureInterestShape::new(move || {
                following_shape(&runtime.active_slot, &runtime.follow_set)
            })),
            Self::Network => Arc::new(ClosureInterestShape::new(network_shape)),
            Self::Author(pubkey) => {
                let pubkey = pubkey.clone();
                Arc::new(ClosureInterestShape::new(move || author_shape(&pubkey)))
            }
        }
    }
}

fn following_shape(
    slot: &ActiveAccountSlot,
    follow_set: &ActiveFollowSet,
) -> Option<InterestShape> {
    let active = slot.lock().ok().and_then(|guard| guard.clone())?;
    let mut authors: BTreeSet<String> = follow_set.follows().into_iter().collect();
    authors.insert(active);
    Some(InterestShape::timeline_for(
        authors,
        picture_acquisition_kinds(),
    ))
}

fn author_shape(pubkey: &str) -> Option<InterestShape> {
    Some(InterestShape::timeline_for(
        BTreeSet::from([pubkey.to_string()]),
        picture_acquisition_kinds(),
    ))
}

fn network_shape() -> Option<InterestShape> {
    let kinds = picture_acquisition_kinds()
        .into_iter()
        .map(|kind| kind.to_string())
        .collect::<Vec<_>>()
        .join(",");
    InterestShape::from_filter_json(&format!(r#"{{"kinds":[{kinds}]}}"#))
}

fn open_feed_acquisition(app: *mut NmpApp, consumer: &str, mode: &FeedMode) {
    match mode {
        FeedMode::Following => {
            close_network_acquisition(app);
            let Ok(kinds) = CString::new(PICTURE_PRIMARY_KINDS_JSON) else {
                return;
            };
            nmp_app_open_contact_feed(app, kinds.as_ptr());
        }
        FeedMode::Network => {
            nmp_app_close_contact_feed(app);
            open_global_acquisition(app, consumer, mode.limit());
        }
        FeedMode::Author(pubkey) => {
            open_author_acquisition(app, pubkey.as_str(), consumer, mode.limit());
        }
    }
}

fn open_global_acquisition(app: *mut NmpApp, consumer: &str, limit: u64) {
    for filter in acquisition_filter_jsons(limit) {
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_app_open_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

fn close_network_acquisition(app: *mut NmpApp) {
    for filter in acquisition_filter_jsons(FeedMode::Network.limit()) {
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(NETWORK_FEED_KEY) else {
            return;
        };
        nmp_app_close_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

fn open_author_acquisition(app: *mut NmpApp, pubkey: &str, consumer: &str, limit: u64) {
    for filter in author_acquisition_filter_jsons(pubkey, limit) {
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_app_open_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

fn close_author_acquisition(app: *mut NmpApp, pubkey: &str, consumer: &str) {
    for filter in
        author_acquisition_filter_jsons(pubkey, FeedMode::Author(pubkey.to_string()).limit())
    {
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_app_close_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

fn c_string_opt(ptr: *const c_char) -> Option<String> {
    if ptr.is_null() {
        return None;
    }
    unsafe { CStr::from_ptr(ptr) }
        .to_str()
        .ok()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_string)
}

#[cfg(test)]
#[path = "picture_feed_test_support.rs"]
mod picture_feed_test_support;
#[cfg(test)]
#[path = "picture_feed_tests.rs"]
mod tests;
