// notifications.rs — P3-B: Rust-side notification grouping.
//
// Groups incoming individual notifications by (kind, target_post_id) so the
// UI renders "alice, bob +N others liked your photo" as ONE row instead of N
// separate rows.  Time-section assignment (Today / This Week / Earlier) is
// presentation-only and left to the native layer.

use std::collections::{HashMap, HashSet};
use std::ffi::{CStr, CString};
use std::os::raw::c_char;

use serde::{Deserialize, Serialize};

/// Individual notification payload (shape emitted by `olas_notification_json`).
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RawNotification {
    #[allow(dead_code)]
    id: String,
    kind: String,
    actor_pubkey: String,
    post_id: Option<String>,
    created_at: u64,
    zap_sats: Option<u64>,
}

/// Grouped notification shape returned to the native layer.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GroupedNotification {
    /// Stable group key, e.g. `"reaction:abc123"` or `"follow:"`.
    pub group_id: String,
    pub kind: String,
    pub target_post_id: Option<String>,
    /// Ordered list of distinct actor pubkeys (first-seen first).
    pub actor_pubkeys: Vec<String>,
    /// Total distinct-actor count.
    pub count: u32,
    /// Unix timestamp of the most-recent event in this group.
    pub latest_ts: u64,
    /// For zap groups: the highest single-zap amount seen (NOT a sum).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub zap_sats: Option<u64>,
}

/// Group a JSON array of individual notifications (as returned by
/// `olas_notification_json`) into a smaller array of clustered rows,
/// one per `(kind, target_post_id)` pair.
///
/// Input: JSON array of notification objects:
///   `[{id, kind, actorPubkey, postId?, createdAt, zapSats?}, …]`
///
/// Output: JSON array of `GroupedNotification` objects, sorted most-recent first:
///   `[{groupId, kind, targetPostId?, actorPubkeys, count, latestTs, zapSats?}]`
///
/// Rules:
/// - Grouping key = `kind + ":" + post_id` (empty string when `post_id` is absent).
/// - An actor that already appears in a group does NOT increase `count` a second time.
/// - For zap groups `zapSats` reports the MAXIMUM single-amount seen (not a running sum).
/// - Groups are sorted by `latest_ts` descending.
///
/// Returned string must be freed with `nmp_free_string`.
#[no_mangle]
pub extern "C" fn olas_group_notifications_json(
    notifications_json: *const c_char,
) -> *mut c_char {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> *mut c_char {
            if notifications_json.is_null() {
                return std::ptr::null_mut();
            }
            let input = match unsafe { CStr::from_ptr(notifications_json) }.to_str() {
                Ok(s) => s,
                Err(_) => return std::ptr::null_mut(),
            };
            let items: Vec<RawNotification> = match serde_json::from_str(input) {
                Ok(v) => v,
                Err(_) => return std::ptr::null_mut(),
            };

            // Preserve insertion order of group keys.
            let mut order: Vec<String> = Vec::new();
            // Track which actors have already been counted per group.
            let mut seen_actors: HashMap<String, HashSet<String>> = HashMap::new();
            let mut groups: HashMap<String, GroupedNotification> = HashMap::new();

            for item in &items {
                let group_id = format!(
                    "{}:{}",
                    item.kind,
                    item.post_id.as_deref().unwrap_or("")
                );

                if let Some(group) = groups.get_mut(&group_id) {
                    let actors = seen_actors.entry(group_id.clone()).or_default();
                    if actors.insert(item.actor_pubkey.clone()) {
                        group.actor_pubkeys.push(item.actor_pubkey.clone());
                        group.count += 1;
                    }
                    if item.created_at > group.latest_ts {
                        group.latest_ts = item.created_at;
                    }
                    if let Some(sats) = item.zap_sats {
                        group.zap_sats =
                            Some(group.zap_sats.map_or(sats, |prev| prev.max(sats)));
                    }
                } else {
                    order.push(group_id.clone());
                    let mut actors_set = HashSet::new();
                    actors_set.insert(item.actor_pubkey.clone());
                    seen_actors.insert(group_id.clone(), actors_set);
                    groups.insert(
                        group_id.clone(),
                        GroupedNotification {
                            group_id: group_id.clone(),
                            kind: item.kind.clone(),
                            target_post_id: item.post_id.clone(),
                            actor_pubkeys: vec![item.actor_pubkey.clone()],
                            count: 1,
                            latest_ts: item.created_at,
                            zap_sats: item.zap_sats,
                        },
                    );
                }
            }

            // Sort by latest_ts descending (most-recent first).
            let mut result_vec: Vec<&GroupedNotification> =
                order.iter().filter_map(|k| groups.get(k)).collect();
            result_vec.sort_by(|a, b| b.latest_ts.cmp(&a.latest_ts));

            let json = match serde_json::to_string(&result_vec) {
                Ok(s) => s,
                Err(_) => return std::ptr::null_mut(),
            };

            CString::new(json)
                .map(CString::into_raw)
                .unwrap_or(std::ptr::null_mut())
        },
    ));
    result.unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_raw(kind: &str, actor: &str, post: Option<&str>, ts: u64) -> String {
        let post_field = match post {
            Some(p) => format!(r#","postId":"{}""#, p),
            None => String::new(),
        };
        format!(
            r#"{{"id":"{}{}","kind":"{}","actorPubkey":"{}"{},"createdAt":{}}}"#,
            kind, actor, kind, actor, post_field, ts,
        )
    }

    #[test]
    fn groups_duplicate_reactions() {
        let a = make_raw("reaction", "alice", Some("post1"), 100);
        let b = make_raw("reaction", "bob", Some("post1"), 200);
        let c = make_raw("reaction", "alice", Some("post1"), 300); // duplicate
        let input = format!("[{},{},{}]", a, b, c);

        let cs = CString::new(input).unwrap();
        let out = unsafe {
            let raw = olas_group_notifications_json(cs.as_ptr());
            assert!(!raw.is_null());
            let s = CStr::from_ptr(raw).to_string_lossy().into_owned();
            nmp_ffi::nmp_free_string(raw);
            s
        };
        let arr: Vec<serde_json::Value> = serde_json::from_str(&out).unwrap();
        assert_eq!(arr.len(), 1, "same (kind,post) → one group");
        assert_eq!(arr[0]["count"], 2, "alice counted only once");
        assert_eq!(arr[0]["latestTs"], 300);
    }

    #[test]
    fn separate_groups_for_different_posts() {
        let a = make_raw("reaction", "alice", Some("post1"), 100);
        let b = make_raw("reaction", "alice", Some("post2"), 200);
        let input = format!("[{},{}]", a, b);

        let cs = CString::new(input).unwrap();
        let out = unsafe {
            let raw = olas_group_notifications_json(cs.as_ptr());
            assert!(!raw.is_null());
            let s = CStr::from_ptr(raw).to_string_lossy().into_owned();
            nmp_ffi::nmp_free_string(raw);
            s
        };
        let arr: Vec<serde_json::Value> = serde_json::from_str(&out).unwrap();
        assert_eq!(arr.len(), 2, "different post_ids → two groups");
        // Most-recent first: post2 (ts=200) before post1 (ts=100).
        assert_eq!(arr[0]["targetPostId"], "post2");
    }
}
