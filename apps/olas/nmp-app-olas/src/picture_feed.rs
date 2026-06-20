use std::collections::BTreeSet;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::{Arc, Mutex, OnceLock};

use nmp_core::slots::{event_by_id_from_store, ActiveAccountSlot};
use nmp_core::substrate::{empty_suppression_lookup, KernelEvent, SuppressionLookup};
use nmp_core::{KernelEventObserver, TypedProjectionData};
use nmp_feed::{
    ClosureInterestShape, FeedAdvance, FeedApply, PullFeedController, RootFeedSnapshot,
};
use nmp_ffi::{nmp_app_open_interest, NmpApp};
use nmp_nip02::ActiveFollowSet;
use nmp_nip68::{
    picture_acquisition_kinds, picture_feed_observer, picture_feed_predicate, PictureFeed,
    PictureFeedEntry,
};
use nmp_planner::InterestShape;
use nmp_wot::WotBootstrapRuntime;

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

#[derive(Default)]
struct FeedSlots {
    following: Option<Arc<PictureFeed>>,
    network: Option<Arc<PictureFeed>>,
}

static FEED_RUNTIME: OnceLock<Mutex<Option<FeedRuntime>>> = OnceLock::new();
static FEED_SLOTS: OnceLock<Mutex<FeedSlots>> = OnceLock::new();

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
    follow_set.on_change(Box::new(reset_all_picture_feeds));

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
        register_picture_feed(app_ref, consumer.clone(), mode);

        let Ok(filter) = CString::new(acquisition_filter_json(mode.limit())) else {
            return;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_app_open_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), mode.scope());
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
        .map(to_cstring)
        .unwrap_or(std::ptr::null_mut())
}

pub(crate) fn reset_network_picture_feed() {
    if let Some(feed) = FEED_SLOTS
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.network.clone()))
    {
        let _ = feed.reset_for_perspective_change();
    }
}

fn register_picture_feed(app: &NmpApp, key: String, mode: FeedMode) {
    let Some(runtime) = current_runtime() else {
        return;
    };
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
    let advance_feed = feed.clone();
    let advance: FeedAdvance = Arc::new(move || {
        advance_feed.grow_visible_window();
    });
    let controller = PullFeedController::new(provider, app.feed_pull_fn(), apply, advance);
    app.register_feed_with_observer(key.clone(), controller, observer);

    let projection_feed = feed.clone();
    app.register_typed_snapshot_projection(key.clone(), move || {
        let snapshot = projection_feed.snapshot_current_window();
        let payload = serde_json::to_vec(&snapshot).ok()?;
        Some(TypedProjectionData {
            key: key.clone(),
            schema_id: PHOTO_FEED_SCHEMA_ID.to_string(),
            schema_version: PHOTO_FEED_SCHEMA_VERSION,
            file_identifier: PHOTO_FEED_FILE_IDENTIFIER.to_string(),
            payload,
            ..Default::default()
        })
    });

    if let Ok(mut slots) = FEED_SLOTS
        .get_or_init(|| Mutex::new(FeedSlots::default()))
        .lock()
    {
        match mode {
            FeedMode::Following => slots.following = Some(feed),
            FeedMode::Network => slots.network = Some(feed),
        }
    }
}

fn current_runtime() -> Option<FeedRuntime> {
    FEED_RUNTIME
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.clone()))
}

fn reset_all_picture_feeds() {
    if let Some(slots) = FEED_SLOTS.get() {
        if let Ok(guard) = slots.lock() {
            if let Some(feed) = &guard.following {
                let _ = feed.reset_for_perspective_change();
            }
            if let Some(feed) = &guard.network {
                let _ = feed.reset_for_perspective_change();
            }
        }
    }
}

#[derive(Clone, Copy)]
enum FeedMode {
    Following,
    Network,
}

impl FeedMode {
    fn from_contact_flag(contact_list_only: u8) -> Self {
        if contact_list_only != 0 {
            Self::Following
        } else {
            Self::Network
        }
    }

    fn default_key(self) -> &'static str {
        match self {
            Self::Following => FOLLOWING_FEED_KEY,
            Self::Network => NETWORK_FEED_KEY,
        }
    }

    fn limit(self) -> u64 {
        match self {
            Self::Following => 50,
            Self::Network => 100,
        }
    }

    fn scope(self) -> u32 {
        match self {
            Self::Following => 0,
            Self::Network => 1,
        }
    }

    fn predicate(self, runtime: &FeedRuntime) -> nmp_nip68::PictureFeedPredicate {
        match self {
            Self::Following => picture_feed_predicate(runtime.follow_set.predicate()),
            Self::Network => {
                let wot = runtime.wot.clone();
                picture_feed_predicate(Arc::new(move |author| network_allows(&wot, author)))
            }
        }
    }

    fn provider(self, runtime: FeedRuntime) -> Arc<dyn nmp_feed::FeedInterestShape + Send + Sync> {
        match self {
            Self::Following => Arc::new(ClosureInterestShape::new(move || {
                following_shape(&runtime.active_slot, &runtime.follow_set)
            })),
            Self::Network => Arc::new(ClosureInterestShape::new(network_shape)),
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

fn network_shape() -> Option<InterestShape> {
    let kinds = picture_acquisition_kinds()
        .into_iter()
        .map(|kind| kind.to_string())
        .collect::<Vec<_>>()
        .join(",");
    InterestShape::from_filter_json(&format!(r#"{{"kinds":[{kinds}]}}"#))
}

fn acquisition_filter_json(limit: u64) -> String {
    let kinds = picture_acquisition_kinds().into_iter().collect::<Vec<_>>();
    serde_json::json!({ "kinds": kinds, "limit": limit }).to_string()
}

fn photo_feed_posts_json_from_payload(payload: &[u8]) -> Option<String> {
    let snapshot: RootFeedSnapshot<PictureFeedEntry, ()> = serde_json::from_slice(payload).ok()?;
    let posts = snapshot
        .cards
        .iter()
        .filter_map(|row| row.card.record.as_ref())
        .collect::<Vec<_>>();
    serde_json::to_string(&posts).ok()
}

fn network_allows(wot: &Option<Arc<WotBootstrapRuntime>>, candidate: &str) -> bool {
    network_allows_for_preset(wot, candidate, &crate::extras_state::wot_preset())
}

fn network_allows_for_preset(
    wot: &Option<Arc<WotBootstrapRuntime>>,
    candidate: &str,
    preset: &str,
) -> bool {
    if preset == "open" {
        return true;
    }
    let Some(runtime) = wot else {
        return false;
    };
    let Some(viewer) = runtime
        .current_snapshot()
        .and_then(|snapshot| snapshot.active_pubkey)
    else {
        return false;
    };
    let decision = match preset {
        "close" => runtime.score_with_minimum_score(&viewer, candidate, 20),
        _ => runtime.score_with_minimum_score(&viewer, candidate, 10),
    };
    decision.is_some_and(|decision| !decision.hide)
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

fn to_cstring(value: String) -> *mut c_char {
    CString::new(value)
        .map(CString::into_raw)
        .unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn acquisition_filter_derives_kind16_from_primary_picture_kind() {
        let value: serde_json::Value =
            serde_json::from_str(&acquisition_filter_json(50)).expect("json");
        assert_eq!(value["kinds"], serde_json::json!([16, 20]));
        assert_eq!(value["limit"], 50);
    }

    #[test]
    fn network_shape_has_no_author_filter() {
        let shape = network_shape().expect("shape");
        assert_eq!(shape.kinds, [16, 20].into_iter().collect());
        assert!(shape.authors.is_empty());
    }

    #[test]
    fn open_network_preset_allows_without_wot_runtime() {
        assert!(network_allows_for_preset(&None, "pubkey", "open"));
    }

    #[test]
    fn balanced_network_preset_rejects_without_wot_runtime() {
        assert!(!network_allows_for_preset(&None, "pubkey", "balanced"));
    }

    #[test]
    fn snapshot_decoder_returns_canonical_picture_records() {
        let feed = PictureFeed::new(Arc::new(|_| true));
        feed.on_kernel_event(&KernelEvent {
            id: "picture-1".to_string(),
            author: "author-1".to_string(),
            kind: nmp_nip68::KIND_PICTURE_EVENT,
            created_at: 42,
            tags: vec![vec![
                "imeta".to_string(),
                "url https://cdn.example/picture.jpg".to_string(),
                "m image/jpeg".to_string(),
                "dim 800x600".to_string(),
            ]],
            content: "caption".to_string(),
            relay_provenance: vec!["wss://relay.example".to_string()],
        });

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
}
