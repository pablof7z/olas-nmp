use std::sync::Arc;

use nmp_core::slots::new_active_account_slot;
use nmp_core::substrate::{EventId, KernelEvent};
use nmp_core::KernelEventObserver as _;
use nmp_wot::WotBootstrapRuntime;

use super::*;

fn author(n: u16) -> String {
    format!("{n:064x}")
}

fn picture_event(author: &str, tags: serde_json::Value) -> String {
    serde_json::json!({
        "id": "abc",
        "author": author,
        "kind": 20,
        "created_at": 42,
        "tags": tags,
        "content": "hello #olas"
    })
    .to_string()
}

fn picture_event_with_image(author: &str) -> String {
    picture_event(
        author,
        serde_json::json!([
            [
                "imeta",
                "url https://example.com/p.jpg",
                "x abc123",
                "m image/jpeg",
                "dim 800x600",
                "alt beach"
            ],
            ["t", "photography"]
        ]),
    )
}

fn p(pubkey: &str) -> Vec<String> {
    vec!["p".to_string(), pubkey.to_string()]
}

fn kernel_event(author: &str, kind: u32, tags: Vec<Vec<String>>) -> KernelEvent {
    KernelEvent {
        id: EventId::from(format!("{kind:064x}")),
        author: author.to_string(),
        kind,
        created_at: 42,
        tags,
        content: String::new(),
        relay_provenance: Vec::new(),
    }
}

fn runtime_for(active: &str) -> Arc<WotBootstrapRuntime> {
    let slot = new_active_account_slot();
    *slot.lock().expect("active slot") = Some(active.to_string());
    let (tx, _rx) = std::sync::mpsc::channel::<nmp_core::ActorMail>();
    Arc::new(WotBootstrapRuntime::new(
        slot,
        nmp_core::CommandSender::new(tx),
    ))
}

#[test]
fn following_kind20_event_becomes_photo_post_json() {
    let author = author(1);
    let raw = picture_event_with_image(&author);

    let json = filter_photo_post_json(&raw, true, "balanced").expect("post");
    let value: serde_json::Value = serde_json::from_str(&json).expect("json");

    assert_eq!(value["id"], "abc");
    assert_eq!(value["authorPubkey"], author);
    assert_eq!(value["images"][0]["url"], "https://example.com/p.jpg");
    assert_eq!(value["images"][0]["width"], 800);
    assert_eq!(value["hashtags"][0], "photography");
    assert_eq!(value["reactionCount"], 0);
}

#[test]
fn following_feed_rejects_kind20_without_imeta_url() {
    let raw = picture_event(&author(1), serde_json::json!([["imeta", "x abc123"]]));

    assert!(filter_photo_post_json(&raw, true, "balanced").is_none());
}

#[test]
fn network_feed_rejects_without_wot_runtime() {
    clear_wot_runtime();
    let raw = picture_event(
        &author(1),
        serde_json::json!([["imeta", "url https://example.com/p.jpg"]]),
    );

    assert!(filter_photo_post_json(&raw, false, "open").is_none());
}

#[test]
fn network_feed_uses_shared_rust_wot_runtime_for_direct_follow_filtering() {
    let viewer = author(1);
    let followed = author(2);
    let unknown = author(3);
    let runtime = runtime_for(&viewer);

    runtime.on_kernel_event(&kernel_event(&viewer, 3, vec![p(&followed)]));

    assert!(
        filter_photo_post_json_with_runtime(
            &picture_event_with_image(&followed),
            false,
            "balanced",
            Some(Arc::clone(&runtime)),
        )
        .is_some(),
        "network feed should show authors trusted by the active account"
    );
    assert!(
        filter_photo_post_json_with_runtime(
            &picture_event_with_image(&unknown),
            false,
            "balanced",
            Some(runtime),
        )
        .is_none(),
        "network feed should hide unknown authors below the balanced trust floor"
    );
}

#[test]
fn network_feed_applies_wot_preset_thresholds_in_rust() {
    let viewer = author(1);
    let alice = author(2);
    let candidate = author(3);
    let runtime = runtime_for(&viewer);

    runtime.on_kernel_event(&kernel_event(&viewer, 3, vec![p(&alice)]));
    runtime.on_kernel_event(&kernel_event(&alice, 3, vec![p(&candidate)]));

    assert!(
        filter_photo_post_json_with_runtime(
            &picture_event_with_image(&candidate),
            false,
            "balanced",
            Some(Arc::clone(&runtime)),
        )
        .is_some(),
        "balanced preset should admit one second-degree trust edge"
    );
    assert!(
        filter_photo_post_json_with_runtime(
            &picture_event_with_image(&candidate),
            false,
            "close",
            Some(runtime),
        )
        .is_none(),
        "close preset should require stronger trust than one second-degree edge"
    );
}

#[test]
fn network_feed_hides_self_muted_author_even_with_open_preset() {
    let viewer = author(1);
    let muted = author(2);
    let runtime = runtime_for(&viewer);

    runtime.on_kernel_event(&kernel_event(&viewer, 10_000, vec![p(&muted)]));

    assert!(
        filter_photo_post_json_with_runtime(
            &picture_event_with_image(&muted),
            false,
            "open",
            Some(runtime),
        )
        .is_none(),
        "open preset must still honor explicit mute decisions from Rust WOT"
    );
}
