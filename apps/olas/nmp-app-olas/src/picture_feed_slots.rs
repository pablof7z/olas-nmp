use std::collections::BTreeMap;
use std::sync::{Arc, Mutex, OnceLock};

use nmp_nip68::PictureFeed;

use super::{FeedMode, FOLLOWING_FEED_KEY, NETWORK_FEED_KEY};

#[derive(Default)]
struct FeedSlots {
    following: Option<Arc<PictureFeed>>,
    network: Option<Arc<PictureFeed>>,
    authors: BTreeMap<String, Arc<PictureFeed>>,
    searches: BTreeMap<String, SearchSlot>,
}

struct SearchSlot {
    query: String,
    feed: Arc<PictureFeed>,
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

pub(super) fn record_picture_feed(key: &str, mode: &FeedMode, feed: Arc<PictureFeed>) {
    if let Ok(mut slots) = FEED_SLOTS
        .get_or_init(|| Mutex::new(FeedSlots::default()))
        .lock()
    {
        match mode {
            FeedMode::Following => slots.following = Some(feed),
            FeedMode::Network => slots.network = Some(feed),
            FeedMode::Author(_) => {
                slots.authors.insert(key.to_string(), feed);
            }
            FeedMode::Search(query) => {
                slots.searches.insert(
                    key.to_string(),
                    SearchSlot {
                        query: query.clone(),
                        feed,
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
    if let Some(feed) = FEED_SLOTS
        .get()
        .and_then(|slot| slot.lock().ok().and_then(|guard| guard.network.clone()))
    {
        let _ = feed.reset_for_perspective_change();
    }
}

pub(super) fn reset_all_picture_feeds() {
    if let Some(slots) = FEED_SLOTS.get() {
        if let Ok(guard) = slots.lock() {
            if let Some(feed) = &guard.following {
                let _ = feed.reset_for_perspective_change();
            }
            if let Some(feed) = &guard.network {
                let _ = feed.reset_for_perspective_change();
            }
            for feed in guard.authors.values() {
                let _ = feed.reset_for_perspective_change();
            }
            for slot in guard.searches.values() {
                let _ = slot.feed.reset_for_perspective_change();
            }
        }
    }
}

pub(super) fn reset_home_picture_feeds() {
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
