use nmp_feed::RootFeedSnapshot;
use nmp_ffi::NmpApp;
use nmp_nip68::PictureFeedEntry;

pub(super) fn current_photo_feed_json(app: &NmpApp, key: &str) -> Option<String> {
    let projections = app.run_typed_snapshot_projections();
    let entry = projections.iter().find(|entry| entry.key == key)?;
    photo_feed_posts_json_from_payload(&entry.payload)
}

pub(super) fn photo_feed_posts_json_from_payload(payload: &[u8]) -> Option<String> {
    let snapshot: RootFeedSnapshot<PictureFeedEntry, ()> = serde_json::from_slice(payload).ok()?;
    let posts = snapshot
        .cards
        .iter()
        .filter_map(|row| {
            let record = row.card.record.as_ref()?;
            let mut value = serde_json::to_value(record).ok()?;
            if let Some(reposted_by) = &row.card.reposted_by {
                value["repostedBy"] = serde_json::to_value(reposted_by).ok()?;
            }
            Some(value)
        })
        .collect::<Vec<_>>();
    serde_json::to_string(&posts).ok()
}
