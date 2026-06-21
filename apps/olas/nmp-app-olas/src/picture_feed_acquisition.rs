use std::ffi::CString;

use nmp_core::KernelEventObserverId;
use nmp_ffi::{nmp_app_close_interest, nmp_app_open_interest, NmpApp};
use nmp_nip68::{picture_acquisition_kinds, KIND_PICTURE_EVENT};
use nmp_planner::InterestShape;

pub(super) fn acquisition_filter_jsons(limit: u64) -> Vec<String> {
    let mut filters =
        vec![serde_json::json!({ "kinds": [KIND_PICTURE_EVENT], "limit": limit }).to_string()];
    filters.extend(
        picture_acquisition_kinds()
            .into_iter()
            .filter(|kind| *kind != KIND_PICTURE_EVENT)
            .map(|kind| serde_json::json!({ "kinds": [kind], "limit": limit }).to_string()),
    );
    filters
}

pub(super) fn author_acquisition_filter_jsons(pubkey: &str, limit: u64) -> Vec<String> {
    let mut filters = vec![serde_json::json!({
        "kinds": [KIND_PICTURE_EVENT],
        "authors": [pubkey],
        "limit": limit,
    })
    .to_string()];
    filters.extend(
        picture_acquisition_kinds()
            .into_iter()
            .filter(|kind| *kind != KIND_PICTURE_EVENT)
            .map(|kind| {
                serde_json::json!({
                    "kinds": [kind],
                    "authors": [pubkey],
                    "limit": limit,
                })
                .to_string()
            }),
    );
    filters
}

pub(super) fn search_acquisition_filter_jsons(query: &str, limit: u64) -> Vec<String> {
    let mut filters = vec![serde_json::json!({
        "kinds": [KIND_PICTURE_EVENT],
        "search": query,
        "limit": limit,
    })
    .to_string()];
    filters.extend(
        picture_acquisition_kinds()
            .into_iter()
            .filter(|kind| *kind != KIND_PICTURE_EVENT)
            .map(|kind| {
                serde_json::json!({
                    "kinds": [kind],
                    "search": query,
                    "limit": limit,
                })
                .to_string()
            }),
    );
    filters
}

pub(super) fn author_feed_key(pubkey: &str) -> String {
    format!("olas.author.{pubkey}")
}

pub(super) fn open_global_acquisition(
    app: *mut NmpApp,
    app_ref: &NmpApp,
    consumer: &str,
    limit: u64,
    observer_id: Option<KernelEventObserverId>,
) {
    for filter in acquisition_filter_jsons(limit) {
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

pub(super) fn close_network_acquisition(app: *mut NmpApp, limit: u64) {
    for filter in acquisition_filter_jsons(limit) {
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(super::NETWORK_FEED_KEY) else {
            return;
        };
        nmp_app_close_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

pub(super) fn open_author_acquisition(
    app: *mut NmpApp,
    app_ref: &NmpApp,
    pubkey: &str,
    consumer: &str,
    limit: u64,
    observer_id: Option<KernelEventObserverId>,
) {
    for filter in author_acquisition_filter_jsons(pubkey, limit) {
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

pub(super) fn close_author_acquisition(app: *mut NmpApp, pubkey: &str, consumer: &str, limit: u64) {
    for filter in author_acquisition_filter_jsons(pubkey, limit) {
        let Ok(filter) = CString::new(filter) else {
            continue;
        };
        let Ok(consumer_cstr) = CString::new(consumer) else {
            return;
        };
        nmp_app_close_interest(app, filter.as_ptr(), consumer_cstr.as_ptr(), 1);
    }
}

pub(super) fn open_observed_acquisition(
    app: &NmpApp,
    filter_json: &str,
    consumer: &str,
    observer_id: Option<KernelEventObserverId>,
) -> bool {
    let Some(observer_id) = observer_id else {
        return false;
    };
    let Some(shape) = InterestShape::from_filter_json(filter_json) else {
        return false;
    };
    app.open_observed_interest(
        filter_json,
        consumer,
        1,
        observer_id,
        vec![shape],
        nmp_feed::DEFAULT_FEED_WINDOW_LIMIT,
    );
    true
}
