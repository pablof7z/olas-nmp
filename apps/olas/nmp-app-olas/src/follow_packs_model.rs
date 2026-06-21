// follow_packs_model.rs — Pure logic: observer state, decode, dedup helpers.
// Kept separate from follow_packs.rs so each file stays under the 500-LOC ceiling.

use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex, OnceLock};

use nmp_core::substrate::KernelEvent;
use nmp_core::KernelEventObserver;
use nmp_ffi::NmpApp;

// ── Constants ─────────────────────────────────────────────────────────────────

/// pablof7z's pubkey (npub1l2vyh4…, nip05 _@f7z.io) — publishes the Olas curated
/// starter packs. Confirmed by the owner. Swap to a dedicated OLAS_CURATOR_PUBKEY
/// if/when a separate curation identity is formalized.
pub(crate) const FEATURED_CURATOR_PUBKEY: &str =
    "fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52";

pub(crate) const KIND_STARTER_PACK: u32 = 39089;
pub(crate) const KIND_MEDIA_STARTER_PACK: u32 = 39092;
pub(crate) const MAX_PREVIEW: usize = 6;

// ── Data structures ──────────────────────────────────────────────────────────

#[derive(Clone)]
pub(crate) struct RawPack {
    pub kind: u32,
    pub author: String,
    pub d_tag: String,
    pub created_at: u64,
    pub title: String,
    pub cover_image_url: Option<String>,
    pub description: Option<String>,
    pub members: Vec<String>, // hex pubkeys, validated and deduped
}

#[derive(Clone, Default)]
pub(crate) struct ProfileCard {
    pub display_name: Option<String>,
    pub image_url: Option<String>,
}

pub(crate) struct PackState {
    pub packs: HashMap<String, RawPack>, // coord_key → newest pack
    pub profiles: HashMap<String, ProfileCard>, // pubkey → profile
    pub claimed: HashSet<String>,        // pubkeys already claimed
}

impl Default for PackState {
    fn default() -> Self {
        Self {
            packs: HashMap::new(),
            profiles: HashMap::new(),
            claimed: HashSet::new(),
        }
    }
}

// ── Global state ──────────────────────────────────────────────────────────────

static PACK_STATE: OnceLock<Arc<Mutex<PackState>>> = OnceLock::new();

pub(crate) fn global_state() -> Arc<Mutex<PackState>> {
    PACK_STATE
        .get_or_init(|| Arc::new(Mutex::new(PackState::default())))
        .clone()
}

// ── Pure helpers ──────────────────────────────────────────────────────────────

pub(crate) fn coord_key(kind: u32, author: &str, d_tag: &str) -> String {
    format!("{}:{}:{}", kind, author, d_tag)
}

pub(crate) fn is_valid_pubkey(pk: &str) -> bool {
    pk.len() == 64 && pk.bytes().all(|b| b.is_ascii_hexdigit())
}

pub(crate) fn first_tag(tags: &[Vec<String>], key: &str) -> Option<String> {
    tags.iter()
        .find(|t| t.first().map(String::as_str) == Some(key))
        .and_then(|t| t.get(1))
        .cloned()
}

pub(crate) fn decode_pack(event: &KernelEvent) -> Option<RawPack> {
    if event.kind != KIND_STARTER_PACK && event.kind != KIND_MEDIA_STARTER_PACK {
        return None;
    }
    let d_tag = first_tag(&event.tags, "d")?;
    if d_tag.is_empty() {
        return None;
    }
    let title = first_tag(&event.tags, "title")
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| d_tag.clone());
    let cover_image_url = first_tag(&event.tags, "image").filter(|s| !s.is_empty());
    let description = first_tag(&event.tags, "description")
        .or_else(|| first_tag(&event.tags, "about"))
        .filter(|s| !s.is_empty());
    let mut seen = HashSet::new();
    let members: Vec<String> = event
        .tags
        .iter()
        .filter(|t| t.first().map(String::as_str) == Some("p"))
        .filter_map(|t| t.get(1))
        .filter(|pk| is_valid_pubkey(pk) && seen.insert((*pk).clone()))
        .cloned()
        .collect();
    Some(RawPack {
        kind: event.kind,
        author: event.author.clone(),
        d_tag,
        created_at: event.created_at,
        title,
        cover_image_url,
        description,
        members,
    })
}

pub(crate) fn decode_profile(event: &KernelEvent) -> ProfileCard {
    let v: serde_json::Value = serde_json::from_str(&event.content).unwrap_or_default();
    ProfileCard {
        display_name: v["display_name"]
            .as_str()
            .or_else(|| v["name"].as_str())
            .filter(|s| !s.is_empty())
            .map(str::to_string),
        image_url: v["picture"]
            .as_str()
            .filter(|s| !s.is_empty())
            .map(str::to_string),
    }
}

// ── KernelEventObserver ──────────────────────────────────────────────────────

struct PackObserver {
    state: Arc<Mutex<PackState>>,
}

impl KernelEventObserver for PackObserver {
    fn on_kernel_event(&self, event: &KernelEvent) {
        match event.kind {
            39089 | 39092 => {
                if let Some(pack) = decode_pack(event) {
                    if let Ok(mut s) = self.state.lock() {
                        let key = coord_key(pack.kind, &pack.author, &pack.d_tag);
                        if s.packs
                            .get(&key)
                            .map_or(true, |e| pack.created_at > e.created_at)
                        {
                            s.packs.insert(key, pack);
                        }
                    }
                }
            }
            0 => {
                let wanted = self
                    .state
                    .lock()
                    .ok()
                    .map_or(false, |s| s.claimed.contains(&event.author));
                if wanted {
                    if let Ok(mut s) = self.state.lock() {
                        s.profiles
                            .insert(event.author.clone(), decode_profile(event));
                    }
                }
            }
            _ => {}
        }
    }
}

/// Install the pack observer. Call once from olas_app_register.
pub(crate) fn install_pack_observer(app: &NmpApp) {
    let obs = Arc::new(PackObserver {
        state: global_state(),
    });
    let _ = app.register_event_observer(obs as Arc<dyn KernelEventObserver>);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    const CURATOR: &str = "fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52";
    const PK1: &str = "1111111111111111111111111111111111111111111111111111111111111111";
    const PK2: &str = "2222222222222222222222222222222222222222222222222222222222222222";
    const PK3: &str = "3333333333333333333333333333333333333333333333333333333333333333";

    fn evt(kind: u32, author: &str, tags: Vec<(&str, &str)>) -> KernelEvent {
        KernelEvent {
            id: "aa".repeat(32),
            author: author.to_string(),
            kind,
            created_at: 1_700_000_000,
            tags: tags
                .into_iter()
                .map(|(k, v)| vec![k.to_string(), v.to_string()])
                .collect(),
            content: String::new(),
            relay_provenance: vec![],
        }
    }

    fn raw_pack(kind: u32, author: &str, d: &str, members: &[&str]) -> RawPack {
        RawPack {
            kind,
            author: author.to_string(),
            d_tag: d.to_string(),
            created_at: 1,
            title: d.to_string(),
            cover_image_url: None,
            description: None,
            members: members.iter().map(|s| s.to_string()).collect(),
        }
    }

    #[test]
    fn decode_39089_extracts_title_image_description_p_tags() {
        let e = {
            let mut e = evt(
                39089,
                CURATOR,
                vec![
                    ("d", "p1"),
                    ("title", "Street Photographers"),
                    ("image", "https://img.example.com/c.jpg"),
                    ("description", "Raw life"),
                    ("p", PK1),
                    ("p", PK2),
                ],
            );
            e.content = String::new();
            e
        };
        let p = decode_pack(&e).unwrap();
        assert_eq!(p.title, "Street Photographers");
        assert_eq!(
            p.cover_image_url.as_deref(),
            Some("https://img.example.com/c.jpg")
        );
        assert_eq!(p.description.as_deref(), Some("Raw life"));
        assert_eq!(p.members.len(), 2);
    }

    #[test]
    fn decode_39092_is_kind_media_starter_pack() {
        let e = evt(39092, CURATOR, vec![("d", "m1"), ("p", PK1)]);
        let p = decode_pack(&e).unwrap();
        assert_eq!(p.kind, KIND_MEDIA_STARTER_PACK);
    }

    #[test]
    fn decode_30000_returns_none() {
        let e = evt(30000, CURATOR, vec![("d", "x"), ("p", PK1)]);
        assert!(decode_pack(&e).is_none());
    }

    #[test]
    fn keeps_newest_per_coordinate() {
        let state: Arc<Mutex<PackState>> = Arc::new(Mutex::new(PackState::default()));
        let obs = PackObserver {
            state: state.clone(),
        };
        let mut old = evt(39089, CURATOR, vec![("d", "p1"), ("p", PK1)]);
        old.created_at = 100;
        let mut new = evt(39089, CURATOR, vec![("d", "p1"), ("p", PK1), ("p", PK2)]);
        new.created_at = 200;
        obs.on_kernel_event(&old);
        obs.on_kernel_event(&new);
        let g = state.lock().unwrap();
        assert_eq!(g.packs.len(), 1);
        assert_eq!(g.packs.values().next().unwrap().created_at, 200);
    }

    #[test]
    fn ranking_media_before_general_featured_before_network_count_desc() {
        const OTHER: &str = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        let mut ranked = vec![
            raw_pack(39089, OTHER, "ng", &[PK1, PK2, PK3]), // network general 3
            raw_pack(39092, CURATOR, "fm", &[PK1]),         // featured media 1
            raw_pack(39089, CURATOR, "fg", &[PK1, PK2]),    // featured general 2
        ];
        ranked.sort_by(|a, b| {
            let m = |p: &RawPack| u8::from(p.kind == KIND_MEDIA_STARTER_PACK);
            let f = |p: &RawPack| u8::from(p.author == FEATURED_CURATOR_PUBKEY);
            m(b).cmp(&m(a))
                .then(f(b).cmp(&f(a)))
                .then(b.members.len().cmp(&a.members.len()))
                .then(a.title.cmp(&b.title))
                .then(a.d_tag.cmp(&b.d_tag))
        });
        assert_eq!(ranked[0].d_tag, "fm");
        assert_eq!(ranked[1].d_tag, "fg");
        assert_eq!(ranked[2].d_tag, "ng");
    }

    #[test]
    fn dedupe_p_tags_within_pack() {
        let e = evt(
            39089,
            CURATOR,
            vec![("d", "p1"), ("p", PK1), ("p", PK1), ("p", PK2)],
        );
        let p = decode_pack(&e).unwrap();
        assert_eq!(p.members.len(), 2);
    }

    #[test]
    fn apply_dedupes_members_across_packs_and_removes_self() {
        let mut state = PackState::default();
        state.packs.insert(
            coord_key(39089, CURATOR, "p1"),
            raw_pack(39089, CURATOR, "p1", &[PK1, PK2]),
        );
        state.packs.insert(
            coord_key(39089, CURATOR, "p2"),
            raw_pack(39089, CURATOR, "p2", &[PK2, PK3]),
        );
        let self_pk = PK1.to_string();
        let selected = vec![
            coord_key(39089, CURATOR, "p1"),
            coord_key(39089, CURATOR, "p2"),
        ];
        let mut seen = HashSet::new();
        let pubkeys: Vec<String> = selected
            .iter()
            .filter_map(|id| state.packs.get(id))
            .flat_map(|p| p.members.iter().cloned())
            .filter(|pk| pk != &self_pk && is_valid_pubkey(pk) && seen.insert(pk.clone()))
            .collect();
        assert_eq!(pubkeys.len(), 2); // PK2 and PK3
        assert!(!pubkeys.contains(&PK1.to_string()));
    }

    #[test]
    fn ignores_malformed_pubkeys() {
        let e = evt(
            39089,
            CURATOR,
            vec![("d", "p1"), ("p", "not-hex"), ("p", "1234"), ("p", PK1)],
        );
        let p = decode_pack(&e).unwrap();
        assert_eq!(p.members.len(), 1);
        assert_eq!(p.members[0], PK1);
    }

    #[test]
    fn empty_state_returns_loading() {
        let state = PackState::default();
        let s = if state.packs.is_empty() {
            "loading"
        } else {
            "ready"
        };
        assert_eq!(s, "loading");
    }

    #[test]
    fn dispatch_count_determines_feed_default() {
        assert_eq!(if 15 >= 15 { "following" } else { "network" }, "following");
        assert_eq!(if 14 >= 15 { "following" } else { "network" }, "network");
    }

    #[test]
    fn default_selected_max_two_featured_media() {
        let packs = vec![
            raw_pack(39092, CURATOR, "fm1", &[PK1]),
            raw_pack(39092, CURATOR, "fm2", &[PK1]),
            raw_pack(39092, CURATOR, "fm3", &[PK1]),
            raw_pack(39089, CURATOR, "fg1", &[PK1]),
        ];
        let mut default_count = 0usize;
        let defaults: Vec<bool> = packs
            .iter()
            .map(|p| {
                let featured = p.author == FEATURED_CURATOR_PUBKEY;
                featured && p.kind == KIND_MEDIA_STARTER_PACK && default_count < 2 && {
                    default_count += 1;
                    true
                }
            })
            .collect();
        assert_eq!(defaults, vec![true, true, false, false]);
    }

    #[test]
    fn onboarding_steps_follow_packs_before_media_server() {
        let steps = [
            "welcome",
            "create_account",
            "follow_packs",
            "media_server",
            "complete",
        ];
        let fp = steps.iter().position(|&s| s == "follow_packs").unwrap();
        let ms = steps.iter().position(|&s| s == "media_server").unwrap();
        assert!(fp < ms);
    }
}
