use nmp_nip68::{picture_acquisition_kinds, KIND_PICTURE_EVENT};

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
