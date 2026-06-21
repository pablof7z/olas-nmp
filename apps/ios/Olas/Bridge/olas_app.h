#pragma once
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

// ── nmp-ffi core (Swift calls these directly) ────────────────────────────────

// Lifecycle
void* nmp_app_new(void);
void  nmp_app_free(void* app);
// nmp_app_start(app, events_per_second, visible_limit, emit_hz)
void  nmp_app_start(void* app, uint32_t events_per_second, uint32_t visible_limit, uint32_t emit_hz);
void  nmp_app_stop(void* app);
// nmp_app_configure(app, events_per_second, visible_limit, emit_hz)
void  nmp_app_configure(void* app, uint32_t events_per_second, uint32_t visible_limit, uint32_t emit_hz);

// Storage (call before nmp_app_start)
uint32_t nmp_app_set_storage_path(void* app, const char* path);

// Update callback (push model — NOT polling)
typedef void (*NmpUpdateCallback)(void* context, const uint8_t* data, size_t len);
void nmp_app_set_update_callback(void* app, void* context, NmpUpdateCallback callback);

// Projection consumption intent (call after nmp_app_new, before nmp_app_start)
void nmp_app_consume_all_builtin_projections(void* app);
void nmp_app_declare_consumed_projections(void* app, const char* projections_json);
uint32_t nmp_app_declare_incremental_apply(void* app);

// Identity
void nmp_app_create_new_account(void* app, const char* profile_json, const char* relays_json, bool mls, uint8_t make_active);
void nmp_app_signin_nsec(void* app, const char* secret, uint8_t make_active);
void nmp_app_signin_bunker(void* app, const char* uri, uint8_t make_active);
void nmp_app_signin_nip55(void* app, const char* signer_package);
void nmp_app_switch_active(void* app, const char* identity_id);
void nmp_app_remove_account(void* app, const char* identity_id);

// Relay
void nmp_app_add_relay(void* app, const char* url, const char* role);
void nmp_app_remove_relay(void* app, const char* url);

// Feed / interests
void nmp_app_open_interest(void* app, const char* filter_json, const char* consumer_id, uint32_t scope);
void nmp_app_close_interest(void* app, const char* filter_json, const char* consumer_id, uint32_t scope);
void nmp_app_open_uri(void* app, const char* uri);
// force=1: re-fetch from relay even if kind:0 is already in the EventStore.
// Profile data is delivered via the update callback `claimed_profiles` projection,
// NOT via the event observer (observer only fires for events NEW to the EventStore).
void nmp_app_claim_profile(void* app, const char* pubkey, const char* consumer_id, int32_t force);
void nmp_app_release_profile(void* app, const char* pubkey, const char* consumer_id);
void nmp_app_claim_event(void* app, const char* event_id, const char* consumer_id);
void nmp_app_release_event(void* app, const char* event_id, const char* consumer_id);
void nmp_app_load_older_feed(void* app, const char* key);

// Actions (Blossom, zap, follow all go through dispatch_action)
char* nmp_app_dispatch_action(void* app, const char* namespace, const char* action_json);
void  nmp_app_ack_action_stage(void* app, const char* correlation_id);
// NOTE: async action terminals (the BUD-02 blob descriptor for
// nmp.blossom.upload, the publish verdict for nmp.publish) are NOT delivered via
// a dedicated observer — the kernel writes them into the snapshot's
// `projections.action_results` per-tick drain, consumed through the event
// observer below. The shell matches them by correlation_id.

// Events
// KernelEventObserverFn = extern "C" fn(*mut c_void, *const c_char) — 2 params, NOT 3.
// Payload is a nul-terminated JSON string {id, author, kind, created_at, tags, content}.
typedef void (*NmpEventObserverCallback)(void* context, const char* event_json);
uint64_t nmp_app_register_event_observer(void* app, void* context, NmpEventObserverCallback callback);
void     nmp_app_unregister_event_observer(void* app, uint64_t id);

// Publish
char* nmp_app_sign_event_for_return(void* app, const char* event_json);
void  nmp_app_retry_publish(void* app, const char* handle);
void  nmp_app_cancel_publish(void* app, const char* handle);

// Profile encoding (NIP-19)
char* nmp_app_encode_profile(void* app, const char* pubkey_hex);

// Lifecycle foreground/background hints
void nmp_app_lifecycle_foreground(void* app);
void nmp_app_lifecycle_background(void* app);

// Free — every char* returned by any nmp_app_* or olas_* function must be freed here
void nmp_free_string(char* s);

// ── Olas-specific additions ───────────────────────────────────────────────────

/// Declare the four canonical Olas relays before nmp_app_start.
/// Normal startup gets this through olas_app_register.
uint32_t olas_declare_initial_relays(void* app);

/// Seed the four canonical Olas relays on a running app after an explicit reset.
/// Adds relay.damus.io, nos.lol, relay.primal.net (role "both") and
/// purplepag.es (role "indexer"). Safe to call again after a relay-config reset.
void olas_seed_default_relays(void* app);

/// Open Olas search interests for the given query string. Profile search keeps
/// a profile-only kind:0 interest; photo search is a typed kind:20 photo-feed
/// projection under consumer_id.
void olas_open_search_feed(void* app, const char* query, const char* consumer_id);

/// Close the search interests opened with olas_open_search_feed.
/// Must be called with the same query and consumer_id as the matching open call.
void olas_close_search_feed(void* app, const char* query, const char* consumer_id);

/// Create a new Nostr account. Constructs profile JSON in Rust and calls
/// nmp_app_create_new_account with empty relays and make_active=1.
void olas_create_account(void* app, const char* name, const char* username);

/// Register Olas protocol extensions (call once after nmp_app_new, before nmp_app_start).
/// Wires nmp-defaults (follow/unfollow/react/zap/routing) and nmp-blossom upload action.
void olas_app_register(void* app);

/// Open NIP-68 primary kind:20 photo feed.
/// contact_list_only=1 -> Following feed (reactive active-account follow set).
/// contact_list_only=0 -> Network feed (global relay pull with Rust WoT filter).
/// NMP derives kind:16 repost-wrapper acquisition from the primary declaration.
void olas_open_photo_feed(void* app, uint8_t contact_list_only, const char* consumer_id);

/// Decode the Rust-owned photo-feed projection for the requested feed key.
/// Returned string is a JSON array of PhotoPost rows; caller frees with nmp_free_string.
char* olas_decode_snapshot_photo_feed_json(const uint8_t* frame, size_t len, const char* key);

/// Read the current Rust-owned photo-feed projection for the requested feed key.
/// Returned string is a JSON array of PhotoPost rows; caller frees with nmp_free_string.
char* olas_current_photo_feed_json(void* app, const char* key);

/// Open a kind:20 photo feed filtered to a single author (profile grid).
/// consumer_id identifies this subscription; close with olas_close_author_photo_feed.
void olas_open_author_photo_feed(void* app, const char* pubkey, const char* consumer_id);

/// Close an author photo feed opened with olas_open_author_photo_feed.
void olas_close_author_photo_feed(void* app, const char* pubkey, const char* consumer_id);

/// Open a NIP-51 kind:30000 follow pack by NIP-19 address (naddr1... or bare coord).
/// Used during onboarding to hydrate follow pack contents.
void olas_open_follow_pack(void* app, const char* pack_addr);

/// Build the Blossom upload action input JSON for nmp_app_dispatch_action.
///
/// file_path  — local path to the blob file (required).
/// mime_type  — MIME type string, e.g. "image/jpeg" (optional; NULL → kernel sniffs).
/// server_url — BUD-02 server base URL (optional; NULL → "https://blossom.primal.net").
///
/// Returns a JSON string suitable for:
///   nmp_app_dispatch_action(app, "nmp.blossom.upload", <returned_json>)
/// Returned pointer must be freed with nmp_free_string.
char* olas_blossom_upload_input_json(const char* file_path, const char* mime_type, const char* server_url);

/// Decode `claimed_profiles` from a FlatBuffer snapshot frame (update callback).
/// Returns JSON array "[{"pubkey","display_name?","picture_url?","nip05?"}]" or NULL.
/// Profiles come via the update callback — NOT via the event observer.
/// Returned string must be freed with nmp_free_string.
char* olas_decode_snapshot_claimed_profiles_json(const uint8_t* frame, size_t len);

/// Decode `active_account` from a FlatBuffer snapshot frame (update callback).
/// Returns JSON object "{"pubkey":"<hex>"}" when an account is active, NULL otherwise.
/// Use this to discover the active account pubkey from the snapshot.
/// Returned string must be freed with nmp_free_string.
char* olas_decode_snapshot_active_account_json(const uint8_t* frame, size_t len);

/// Decode action results from a FlatBuffer snapshot frame (from the update callback).
/// Returns a JSON array "[{correlation_id, status, result?}]", or NULL if no
/// action results are present in this frame. Returned string must be freed with
/// nmp_free_string. Call from the nmp_app_set_update_callback handler to resolve
/// nmp.blossom.upload and nmp.publish async terminals by correlation_id.
char* olas_decode_snapshot_action_results_json(const uint8_t* frame, size_t len);

/// Build the nmp.publish action input JSON for a NIP-68 (kind:20) picture post
/// from one or more finished Blossom uploads. This is the single canonical
/// picture-post publish entry point shared by both platforms.
///
/// P0-B: replaced the 5-arg single-image signature with an array form so that
/// multi-photo posts emit one kind:20 with multiple NIP-68 `imeta` tags.
///
/// uploaded_images_json — JSON array of per-image descriptors:
///   [{"descriptor":{...BUD-02...},"alt":"alt text","dim":"WxH"}, ...]
///   Each `descriptor` must contain at least `url` and `sha256`. `alt` and
///   `dim` are optional per image. A single-element array produces the same
///   kind:20 as the old single-image form.
/// caption — kind:20 content string (NULL → empty).
/// geohash — 4-char NIP-52 "g" tag value (NULL → omitted). EXIF GPS is always
///   stripped in the native encoding step; only set when user enables location.
///
/// Returns a JSON string suitable for:
///   nmp_app_dispatch_action(app, "nmp.publish", <returned_json>)
/// The kernel's nmp.publish action constructs and signs the kind:20.
/// Returned pointer must be freed with nmp_free_string.
char* olas_picture_post_publish_json(const char* uploaded_images_json, const char* caption, const char* geohash);

// ── P0-A: Follow-pack discovery and bulk-apply ────────────────────────────────

/// Open a NIP-51 kind:30000 follow-pack discovery interest for the canonical
/// Olas curated pack authors. Events arrive via the kernel event observer.
/// Decode each event with olas_decode_follow_pack_event_json to get pack
/// metadata + member pubkeys. Close with olas_close_follow_pack_discovery.
void olas_open_follow_pack_discovery(void* app, const char* consumer_id);

/// Close the follow-pack discovery interest.
void olas_close_follow_pack_discovery(void* app, const char* consumer_id);

/// Decode a raw kind:30000 Nostr event JSON (from the event observer) into a
/// FollowPack descriptor JSON for rendering.
/// Returns: {"id":"<d-tag>","name":"...","description":"...","accent_color":"...",
///           "pubkeys":["<hex>",...],"count":N}
/// Returns NULL when the event is not a valid kind:30000 with p tags.
/// Returned string must be freed with nmp_free_string.
char* olas_decode_follow_pack_event_json(const char* event_json);

/// Apply the selected follow packs. The native side passes the union of all
/// `p`-tag pubkeys from the selected packs (deduplicated, self-excluded).
/// Rust dispatches one `nmp.follow` per pubkey through the action bus.
///
/// pubkeys_json    — JSON array of hex pubkey strings. Required.
/// active_pubkey   — active account hex pubkey for self-exclusion guard
///                   (may be NULL or empty if unknown).
///
/// Returns: {"follow_count":N,"feed_default":"following|network"}
/// feed_default is "following" when N >= 15, "network" otherwise.
/// Returns NULL on empty input or decode error.
/// Returned string must be freed with nmp_free_string.
char* olas_apply_follow_pack_pubkeys(void* app, const char* pubkeys_json, const char* active_pubkey);

/// Live "Following" count for the active account — the number of distinct `p`
/// tags in its current kind:3, read synchronously from the local event store
/// (read-your-writes; reflects a just-applied follow pack with no relay
/// round-trip). Returns -1 when unavailable (no active account / not started);
/// the host renders -1 as 0.
int64_t nmp_app_active_following_count(void* app);

// ── New Olas FFI helpers ──────────────────────────────────────────────────────

// Event decoders (caller must free with nmp_free_string)
char* olas_decode_kind0_event_json(const char* event_json);
char* olas_profile_json(const char* event_json);
char* olas_notification_json(const char* event_json);
char* olas_contact_list_pubkeys_json(const char* event_json, const char* active_pubkey);
char* olas_default_relays_json(void);

// Action builders (caller must free with nmp_free_string)
char* olas_react_action_json(const char* target_event_id, const char* target_author_pubkey);
char* olas_zap_action_json(const char* recipient_pubkey, const char* target_event_id, uint64_t amount_msats, const char* comment);
char* olas_bookmark_event_action_json(const char* account_pubkey, const char* event_id);

// Bolt11 parsing (returns msats or -1 on error; no memory to free)
long long olas_bolt11_amount_msats(const char* bolt11);
uint64_t olas_bolt11_amount_sats(const char* bolt11);

// Geohash (caller must free with nmp_free_string)
char* olas_compute_geohash(double lat, double lon, int precision);
char* olas_location_geohash4(double latitude, double longitude);

// Zap action builder (caller must free with nmp_free_string)
char* olas_build_zap_action_json(const char* event_id, long long sats);

// Zap notification decoder: parses kind:9735 receipt; returns {"amount_sats":N,"referenced_event_id":"..."} or NULL
char* olas_decode_zap_notification_json(const char* event_json);

// Config providers (caller must free with nmp_free_string)
char* olas_filter_catalog_json(void);
char* olas_media_upload_config_json(void);
char* olas_picker_config_json(void);
char* olas_settings_catalog_json(void);
char* olas_onboarding_steps_json(void);
char* olas_compose_steps_json(void);

// Blossom server URL (caller must free get result with nmp_free_string)
char* olas_blossom_server_url_get(void* app);
void  olas_blossom_server_url_set(void* app, const char* url);

// Feed mode (caller must free get result with nmp_free_string)
char* olas_feed_mode_get(void* app);
void  olas_feed_mode_set(void* app, const char* mode);

// Network-feed WoT preset (caller must free get result with nmp_free_string)
char* olas_wot_preset_get(void* app);
void  olas_wot_preset_set(void* app, const char* preset);

// ── P2-A/P2-C: Invite links ───────────────────────────────────────────────────

/// Resolve an invite token → inviter info.
/// `token` accepts https://olas.app/i/<npub>, olas://i/<npub>, bare npub1..., or hex pubkey.
/// Returns: `{"inviter_pubkey":"<hex>","display_hint":"npub1..."}` or NULL.
/// Returned string must be freed with nmp_free_string.
char* olas_resolve_invite_json(const char* token);

/// Mint the canonical invite link for the active user.
/// `active_pubkey` — 64-char hex pubkey of the signed-in account.
/// Returns: `"https://olas.app/i/<npub>"` or NULL.
/// Returned string must be freed with nmp_free_string.
char* olas_my_invite_link(const char* active_pubkey);

// ── P0-E: Real social proof ───────────────────────────────────────────────────

/// Query the real social proof for target_pubkey from active_pubkey's follow graph.
/// Returns {"mutual_followers":["<hex>"...],"mutual_count":N,"reason_kind":"followed_by_mutuals"|"new_account"}
/// reason_kind is "followed_by_mutuals" when at least one of the viewer's direct follows also
/// follows the target.  "new_account" is the honest fallback — NOT a fake claim.
/// Returns NULL when active_pubkey is empty or the WoT graph is not yet bootstrapped.
/// Returned string must be freed with nmp_free_string.
char* olas_social_proof_json(void* app, const char* active_pubkey, const char* target_pubkey);

// ── P0-F: Ranked discover sections ───────────────────────────────────────────

/// Return ranked discover sections for active_pubkey, computed from the WoT follow graph.
/// JSON: [{"title":"...","reason":"...","profiles":[{"pubkey":"...","mutual_count":N}]}]
/// reason is "second_degree" (Popular in your circles) or "graph_empty" (honest fallback).
/// Returns NULL when active_pubkey is empty or the WoT runtime is absent.
/// Returned string must be freed with nmp_free_string.
char* olas_discover_sections_json(void* app, const char* active_pubkey);

// ── P3-B: Grouped notifications ──────────────────────────────────────────────

/// Group a JSON array of individual notification payloads (as emitted by
/// olas_notification_json) into a clustered array, one row per (kind, post_id).
///
/// Input: JSON array of notification objects:
///   [{id, kind, actorPubkey, postId?, createdAt, zapSats?}, …]
/// Output: JSON array sorted most-recent first:
///   [{groupId, kind, targetPostId?, actorPubkeys:[hex,…], count, latestTs, zapSats?}]
///
/// Returns NULL on null / empty / unparseable input.
/// Returned string must be freed with nmp_free_string.
char* olas_group_notifications_json(const char* notifications_json);

// ── P3-C: Caption tag parsing (NIP-27 mentions + hashtags) ──────────────────

/// Parse nostr: mentions and #hashtag tokens from a caption string.
///
/// Input: caption text (may contain nostr:npub1… URIs and #word tokens).
/// Output JSON: {"content":"<caption>","p_tags":[["p","<hex>"],…],"t_tags":[["t","<tag>"],…]}
///
/// Returned string must be freed with nmp_free_string.
char* olas_parse_caption_tags_json(const char* caption);

/// Extended picture-post publish that merges additional NIP tags.
///
/// Same as olas_picture_post_publish_json but extra_tags_json (a JSON array of
/// tag arrays, e.g. [["p","<hex>"],["t","bitcoin"]]) is merged into the kind:20
/// tags.  Pass NULL for extra_tags_json to behave identically to the base function.
///
/// Returned string must be freed with nmp_free_string.
char* olas_picture_post_publish_tagged_json(const char* uploaded_images_json,
                                             const char* caption,
                                             const char* geohash,
                                             const char* extra_tags_json);

// ── P3-D: Account Recovery Key export ────────────────────────────────────────

/// Export the active account's secret key as a Recovery Key string (bech32).
///
/// Returns the bech32 secret for the active local account, or NULL when no local
/// account is signed in (remote signer / no account).
///
/// SECURITY: the caller MUST NOT log the returned string. Present it only through
/// the "Back up account" UI flow. Free with nmp_free_string immediately after use.
char* olas_active_account_recovery_key(void* app);
