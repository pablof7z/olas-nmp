use std::collections::BTreeMap;
use std::sync::{Arc, Mutex, OnceLock};

use nmp_feed::{FeedController, PullFeedController};

use super::{FeedMode, FOLLOWING_FEED_KEY, NETWORK_FEED_KEY};

// Slots hold the pull-backed controller (not the bare `PictureFeed`) so a
// perspective change resets through `PullFeedController::reset`: rewinding the
// pull cursor to seq 0 AND clearing the visible window via the wired reset hook.
// Resetting the feed alone would clear visible state but leave the cursor
// advanced, so the next `load_older` would resume past the rewound history.
#[derive(Default)]
struct FeedSlots {
    following: Option<Arc<PullFeedController>>,
    network: Option<Arc<PullFeedController>>,
    authors: BTreeMap<String, Arc<PullFeedController>>,
    searches: BTreeMap<String, SearchSlot>,
}

struct SearchSlot {
    query: String,
    controller: Arc<PullFeedController>,
}

static FEED_SLOTS: OnceLock<Mutex<FeedSlots>> = OnceLock::new();

pub(super) fn clear_picture_feed_slots() {
    if let Ok(mut slots) = FEED_SLOTS
        .get_or_init(|| Mutex::new(FeedSlots::default()))
        .lock()
    {
        *slots = FeedSlots::default();
    }
}

pub(super) fn picture_feed_registered(key: &str, mode: &FeedMode) -> bool {
    FEED_SLOTS
        .get()
        .and_then(|slot| slot.lock().ok())
        .is_some_and(|slots| match mode {
            FeedMode::Following => key == FOLLOWING_FEED_KEY && slots.following.is_some(),
            FeedMode::Network => key == NETWORK_FEED_KEY && slots.network.is_some(),
            FeedMode::Author(_) => slots.authors.contains_key(key),
            FeedMode::Search(query) => slots
                .searches
                .get(key)
                .is_some_and(|slot| slot.query == *query),
        })
}

pub(super) fn record_picture_feed(key: &str, mode: &FeedMode, controller: Arc<PullFeedController>) {
    if let Ok(mut slots) = FEED_SLOTS
        .get_or_init(|| Mutex::new(FeedSlots::default()))
        .lock()
    {
        match mode {
            FeedMode::Following => slots.following = Some(controller),
            FeedMode::Network => slots.network = Some(controller),
            FeedMode::Author(_) => {
                slots.authors.insert(key.to_string(), controller);
            }
            FeedMode::Search(query) => {
                slots.searches.insert(
                    key.to_string(),
                    SearchSlot {
                        query: query.clone(),
                        controller,
                    },
                );
            }
        }
    }
}

pub(super) fn remove_author_picture_feed(key: &str) {
    if let Some(slots) = FEED_SLOTS.get() {
        if let Ok(mut guard) = slots.lock() {
            guard.authors.remove(key);
        }
    }
}

pub(super) fn remove_search_picture_feed(key: &str) {
    if let Some(slots) = FEED_SLOTS.get() {
        if let Ok(mut guard) = slots.lock() {
            guard.searches.remove(key);
        }
    }
}

pub(super) fn search_picture_feed_query(key: &str) -> Option<String> {
    FEED_SLOTS.get().and_then(|slots| {
        slots
            .lock()
            .ok()
            .and_then(|guard| guard.searches.get(key).map(|slot| slot.query.clone()))
    })
}

pub(crate) fn reset_network_picture_feed() {
    if let Some(controller) = FEED_SLOTS
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.network.clone()))
    {
        let _ = controller.reset();
    }
}

pub(super) fn reset_all_picture_feeds() {
    if let Some(slots) = FEED_SLOTS.get() {
        if let Ok(guard) = slots.lock() {
            if let Some(controller) = &guard.following {
                let _ = controller.reset();
            }
            if let Some(controller) = &guard.network {
                let _ = controller.reset();
            }
            for controller in guard.authors.values() {
                let _ = controller.reset();
            }
            for slot in guard.searches.values() {
                let _ = slot.controller.reset();
            }
        }
    }
}

pub(super) fn reset_home_picture_feeds() {
    if let Some(slots) = FEED_SLOTS.get() {
        if let Ok(guard) = slots.lock() {
            if let Some(controller) = &guard.following {
                let _ = controller.reset();
            }
            if let Some(controller) = &guard.network {
                let _ = controller.reset();
            }
        }
    }
}
