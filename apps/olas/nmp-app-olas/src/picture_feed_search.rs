use std::ffi::CString;

use nmp_ffi::{nmp_app_close_interest, nmp_app_open_interest, NmpApp};
use nmp_nip68::picture_acquisition_kinds;
use nmp_planner::InterestShape;

use super::picture_feed_acquisition::{open_observed_acquisition, search_acquisition_filter_jsons};
use super::picture_feed_slots::{remove_search_picture_feed, search_picture_feed_query};
use super::{register_picture_feed, FeedMode};

pub(crate) fn open_search_picture_feed(app: *mut NmpApp, query: &str, consumer: &str) {
    if app.is_null() {
        return;
    }
    let query = query.trim();
    if query.is_empty() {
        return;
    }
    if let Some(previous) = search_picture_feed_query(consumer) {
        if previous != query {
            close_search_acquisition(app, &previous, consumer);
            // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
            let app_ref = unsafe { &*app };
            let _ = app_ref.unregister_feed(consumer);
            remove_search_picture_feed(consumer);
        }
    }
    let mode = FeedMode::Search(query.to_string());

    // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
    let app_ref = unsafe { &*app };
    let observer_id = register_picture_feed(app_ref, consumer.to_string(), mode.clone());
    super::open_feed_acquisition(app, app_ref, consumer, &mode, observer_id);
}

pub(crate) fn close_search_picture_feed(app: *mut NmpApp, query: &str, consumer: &str) {
    if app.is_null() {
        return;
    }
    let query = query.trim();
    if query.is_empty() {
        return;
    }
    close_search_acquisition(app, query, consumer);

    // SAFETY: caller provides a live NmpApp pointer from nmp_app_new.
    let app_ref = unsafe { &*app };
    let _ = app_ref.unregister_feed(consumer);
    remove_search_picture_feed(consumer);
}

pub(super) fn search_shape(query: &str) -> Option<InterestShape> {
    let kinds = picture_acquisition_kinds().into_iter().collect::<Vec<_>>();
    let filter = serde_json::json!({
        "kinds": kinds,
        "search": query,
        "limit": FeedMode::Search(query.to_string()).limit(),
    });
    InterestShape::from_filter_json(&filter.to_string())
}

pub(super) fn open_search_acquisition(
    app: *mut NmpApp,
    app_ref: &NmpApp,
    query: &str,
    consumer: &str,
    limit: u64,
    observer_id: Option<nmp_core::KernelEventObserverId>,
) {
    for filter in search_acquisition_filter_jsons(query, limit) {
        if open_observed_acquisition(app_ref, &filter, consumer, observer_id) {
            continue;
        }
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_app_open_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

fn close_search_acquisition(app: *mut NmpApp, query: &str, consumer: &str) {
    for filter in
        search_acquisition_filter_jsons(query, FeedMode::Search(query.to_string()).limit())
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
