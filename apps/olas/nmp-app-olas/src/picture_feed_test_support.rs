use std::ffi::{c_void, CString};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};

use nmp_core::substrate::KernelEvent;
use nmp_core::typed_projections::{
    decode_configured_relays, decode_relay_diagnostics, CONFIGURED_RELAYS_SCHEMA_ID,
    RELAY_DIAGNOSTICS_SCHEMA_ID,
};
use nmp_core::KernelEventObserver;
use nmp_wot::WotBootstrapRuntime;

use super::*;

static FRAME_TX: std::sync::OnceLock<Mutex<Option<Sender<Vec<u8>>>>> = std::sync::OnceLock::new();

pub(super) extern "C" fn capture_frame_callback(_ctx: *mut c_void, ptr: *const u8, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }
    let bytes = unsafe { std::slice::from_raw_parts(ptr, len) }.to_vec();
    if let Some(slot) = FRAME_TX.get() {
        if let Ok(guard) = slot.lock() {
            if let Some(tx) = guard.as_ref() {
                let _ = tx.send(bytes);
            }
        }
    }
}

pub(super) fn install_frame_capture() -> Receiver<Vec<u8>> {
    let (tx, rx) = channel::<Vec<u8>>();
    let slot = FRAME_TX.get_or_init(|| Mutex::new(None));
    *slot.lock().expect("frame sender slot") = Some(tx);
    rx
}

pub(super) fn wait_for_non_empty_network_frame(
    frames: &Receiver<Vec<u8>>,
    timeout: Duration,
    signal: &EventSignal,
) -> Result<String, String> {
    let deadline = Instant::now() + timeout;
    let mut summaries = Vec::new();
    loop {
        let now = Instant::now();
        if now >= deadline {
            return Err(format!(
                "timed out waiting for kind:20 frame; observed={:?}; diagnostics:\n{}",
                signal.last_picture(),
                summaries.join("\n---\n")
            ));
        }
        match frames.recv_timeout(deadline.saturating_duration_since(now)) {
            Ok(frame) => {
                summaries.push(frame_summary(&frame));
                if summaries.len() > 8 {
                    summaries.remove(0);
                }
                if let Some(posts_json) = non_empty_network_posts_json_from_frame(&frame) {
                    return Ok(posts_json);
                }
            }
            Err(_) => {
                return Err(format!(
                    "timed out waiting for kind:20 frame; observed={:?}; diagnostics:\n{}",
                    signal.last_picture(),
                    summaries.join("\n---\n")
                ));
            }
        }
    }
}

fn frame_summary(frame: &[u8]) -> String {
    let mut lines = Vec::new();
    match nmp_core::decode_snapshot_envelope(frame) {
        Ok(envelope) => {
            let statuses = envelope
                .relay_statuses
                .iter()
                .map(|row| format!("{}:{}:ev={}", row.relay_url, row.connection, row.events_rx))
                .collect::<Vec<_>>()
                .join(", ");
            lines.push(format!("relay_statuses=[{statuses}]"));
        }
        Err(error) => lines.push(format!("envelope_error={error:?}")),
    }

    match nmp_core::decode_snapshot_typed_projections(frame) {
        Ok(entries) => {
            let keys = entries
                .iter()
                .map(|entry| entry.key.as_str())
                .collect::<Vec<_>>()
                .join(",");
            lines.push(format!("typed_keys=[{keys}]"));
            for entry in entries {
                append_relay_projection_summary(&mut lines, &entry);
            }
        }
        Err(error) => lines.push(format!("typed_error={error:?}")),
    }
    lines.join("\n")
}

fn append_relay_projection_summary(lines: &mut Vec<String>, entry: &nmp_core::TypedProjectionData) {
    if entry.key == CONFIGURED_RELAYS_SCHEMA_ID {
        if let Ok(model) = decode_configured_relays(&entry.payload) {
            let relays = model
                .relays
                .iter()
                .map(|row| format!("{}:{}", row.url, row.role))
                .collect::<Vec<_>>()
                .join(", ");
            lines.push(format!("configured_relays=[{relays}]"));
        }
    }
    if entry.key == RELAY_DIAGNOSTICS_SCHEMA_ID {
        if let Ok(model) = decode_relay_diagnostics(&entry.payload) {
            let relays = model
                .relays
                .iter()
                .map(|row| {
                    format!(
                        "{}:{}:{}/{} ev={}",
                        row.relay_url,
                        row.connection,
                        row.active_sub_count,
                        row.total_sub_count,
                        row.total_events_rx
                    )
                })
                .collect::<Vec<_>>()
                .join(", ");
            let interests = model
                .interests
                .iter()
                .map(|row| {
                    format!(
                        "{}:{} refs={} relays={:?}",
                        row.key, row.state, row.refcount, row.relay_urls
                    )
                })
                .collect::<Vec<_>>()
                .join(", ");
            lines.push(format!("relay_diagnostics.relays=[{relays}]"));
            lines.push(format!("relay_diagnostics.interests=[{interests}]"));
        }
    }
}

pub(super) fn signed_picture_event(created_at: u64) -> (String, String) {
    use nostr::{EventBuilder, JsonUtil, Keys, Kind, Tag, Timestamp};

    let keys = Keys::generate();
    let imeta = Tag::parse([
        "imeta",
        "url https://cdn.example/picture.jpg",
        "m image/jpeg",
        "dim 800x600",
    ])
    .expect("valid imeta tag");
    let event = EventBuilder::new(
        Kind::from_u16(nmp_nip68::KIND_PICTURE_EVENT as u16),
        "caption",
    )
    .tag(imeta)
    .custom_created_at(Timestamp::from(created_at))
    .sign_with_keys(&keys)
    .expect("signed picture event");
    (event.id.to_hex(), event.as_json())
}

pub(super) fn inject_signed_event(app: *mut nmp_ffi::NmpApp, event_json: &str) {
    let event_json = CString::new(event_json).expect("event json");
    assert!(
        nmp_ffi::nmp_app_inject_signed_event_json(app, event_json.as_ptr()),
        "signed event must inject"
    );
}

pub(super) fn assert_posts_contain(posts_json: &str, event_id: &str) {
    let posts: serde_json::Value = serde_json::from_str(posts_json).expect("posts json");
    let rows = posts.as_array().expect("posts array");
    assert!(
        rows.iter().any(|row| row["event_id"] == event_id),
        "expected posts to contain {event_id}, got {posts_json}"
    );
}

pub(super) fn assert_posts_array_non_empty(posts_json: &str) {
    let posts: serde_json::Value = serde_json::from_str(posts_json).expect("posts json");
    let rows = posts.as_array().expect("posts array");
    assert!(
        !rows.is_empty(),
        "expected non-empty posts, got {posts_json}"
    );
}

#[derive(Default)]
pub(super) struct EventSignal {
    state: Mutex<EventSignalState>,
    changed: Condvar,
}

impl EventSignal {
    pub(super) fn wait_for_non_empty_projection(
        &self,
        timeout: Duration,
        mut read_projection: impl FnMut() -> Option<String>,
    ) -> (Option<(String, String, u64)>, String) {
        let deadline = Instant::now() + timeout;
        loop {
            let before_count = self.picture_count();
            if let Some(posts_json) = read_projection() {
                return (self.last_picture(), posts_json);
            }
            let now = Instant::now();
            assert!(
                now < deadline,
                "timed out waiting for real relay kind:20 projection; observed={:?}",
                self.last_picture()
            );
            let remaining = deadline.saturating_duration_since(now);
            let mut seen = self.state.lock().expect("signal lock");
            while seen.picture_count == before_count {
                let (next, status) = self
                    .changed
                    .wait_timeout(seen, remaining)
                    .expect("wait for relay event");
                seen = next;
                if status.timed_out() {
                    break;
                }
            }
        }
    }

    fn picture_count(&self) -> usize {
        self.state.lock().expect("signal lock").picture_count
    }

    pub(super) fn last_picture(&self) -> Option<(String, String, u64)> {
        self.state.lock().expect("signal lock").last_picture.clone()
    }
}

impl KernelEventObserver for EventSignal {
    fn on_kernel_event(&self, event: &KernelEvent) {
        if event.kind == nmp_nip68::KIND_PICTURE_EVENT {
            if let Ok(mut seen) = self.state.lock() {
                seen.picture_count += 1;
                seen.last_picture =
                    Some((event.id.clone(), event.author.clone(), event.created_at));
                self.changed.notify_all();
            }
        }
    }
}

#[derive(Default)]
struct EventSignalState {
    picture_count: usize,
    last_picture: Option<(String, String, u64)>,
}

pub(super) fn picture(id: &str, author: &str, created_at: u64) -> KernelEvent {
    KernelEvent {
        id: id.to_string(),
        author: author.to_string(),
        kind: nmp_nip68::KIND_PICTURE_EVENT,
        created_at,
        tags: vec![vec![
            "imeta".to_string(),
            "url https://cdn.example/picture.jpg".to_string(),
            "m image/jpeg".to_string(),
            "dim 800x600".to_string(),
        ]],
        content: "caption".to_string(),
        relay_provenance: vec!["wss://relay.example".to_string()],
    }
}

pub(super) fn non_empty_network_posts_json(app: *mut nmp_ffi::NmpApp) -> Option<String> {
    let projections = unsafe { &*app }.run_typed_snapshot_projections();
    non_empty_network_posts_json_from_entries(&projections)
}

fn non_empty_network_posts_json_from_frame(frame: &[u8]) -> Option<String> {
    let projections = nmp_core::decode_snapshot_typed_projections(frame).ok()?;
    non_empty_network_posts_json_from_entries(&projections)
}

fn non_empty_network_posts_json_from_entries(
    projections: &[nmp_core::TypedProjectionData],
) -> Option<String> {
    let feed = projections
        .iter()
        .find(|entry| entry.key == NETWORK_FEED_KEY)?;
    let posts_json = photo_feed_posts_json_from_payload(&feed.payload)?;
    let posts: serde_json::Value = serde_json::from_str(&posts_json).ok()?;
    posts
        .as_array()
        .is_some_and(|rows| !rows.is_empty())
        .then_some(posts_json)
}

pub(super) struct WotFixture {
    app: *mut nmp_ffi::NmpApp,
    pub(super) runtime: Arc<WotBootstrapRuntime>,
}

impl Drop for WotFixture {
    fn drop(&mut self) {
        nmp_ffi::nmp_app_free(self.app);
    }
}

pub(super) fn active_wot_runtime(active: &str) -> WotFixture {
    let app = nmp_ffi::nmp_app_new();
    assert!(!app.is_null());
    let app_ref = unsafe { &*app };
    let slot = app_ref.active_account_handle();
    *slot.lock().expect("active slot") = Some(active.to_string());
    let runtime = Arc::new(WotBootstrapRuntime::new(slot, app_ref.actor_sender()));
    WotFixture { app, runtime }
}

pub(super) fn author(n: u16) -> String {
    format!("{n:064x}")
}

pub(super) fn contact_list(event_author: &str, follows: &[&str]) -> KernelEvent {
    KernelEvent {
        id: nmp_core::substrate::EventId::from("4".repeat(64)),
        author: event_author.to_string(),
        kind: nmp_wot::interest::KIND_CONTACT_LIST,
        created_at: 1_000,
        tags: follows
            .iter()
            .map(|pubkey| vec!["p".to_string(), (*pubkey).to_string()])
            .collect(),
        content: String::new(),
        relay_provenance: Vec::new(),
    }
}

pub(super) fn repost(id: &str, author: &str, target: &KernelEvent, created_at: u64) -> KernelEvent {
    let repost_kind = *picture_acquisition_kinds()
        .iter()
        .find(|kind| **kind == 16)
        .expect("kind:16 generic repost acquisition");
    KernelEvent {
        id: id.to_string(),
        author: author.to_string(),
        kind: repost_kind,
        created_at,
        tags: vec![vec!["e".to_string(), target.id.clone()]],
        content: serde_json::json!({
            "id": target.id,
            "pubkey": target.author,
            "kind": target.kind,
            "created_at": target.created_at,
            "tags": target.tags,
            "content": target.content,
        })
        .to_string(),
        relay_provenance: vec!["wss://relay.example".to_string()],
    }
}
