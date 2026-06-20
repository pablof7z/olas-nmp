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
void nmp_app_open_contact_feed(void* app, const char* kinds_json);
void nmp_app_close_contact_feed(void* app);
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

// Wallet (NWC — Nostr Wallet Connect)
void nmp_app_wallet_connect(void* app, const char* nwc_uri);
char* nmp_app_wallet_pay_invoice(void* app, const char* bolt11);

// Free — every char* returned by any nmp_app_* or olas_* function must be freed here
void nmp_free_string(char* s);

// ── Olas-specific additions ───────────────────────────────────────────────────

/// Seed the four canonical Olas relays (call once after nmp_app_start).
/// Adds relay.damus.io, nos.lol, relay.primal.net (role "both") and
/// purplepag.es (role "indexer"). Safe to call again after a relay-config reset.
void olas_seed_default_relays(void* app);

/// Open a NIP-50 search interest (kinds 0 + 20) for the given query string.
/// Scope is global (1). Close with olas_close_search_feed using the same
/// query and consumer_id.
void olas_open_search_feed(void* app, const char* query, const char* consumer_id);

/// Close the NIP-50 search interest opened with olas_open_search_feed.
/// Must be called with the same query and consumer_id as the matching open call.
void olas_close_search_feed(void* app, const char* query, const char* consumer_id);

/// Create a new Nostr account. Constructs profile JSON in Rust and calls
/// nmp_app_create_new_account with empty relays and make_active=1.
void olas_create_account(void* app, const char* name, const char* username);

/// Register Olas protocol extensions (call once after nmp_app_new, before nmp_app_start).
/// Wires nmp-defaults (follow/unfollow/react/zap/routing) and nmp-blossom upload action.
void olas_app_register(void* app);

/// Open NIP-68 kind:20 photo feed.
/// contact_list_only=1 → Following feed (contact-list scoped, scope 0).
/// contact_list_only=0 → Network feed (global, scope 1).
/// consumer_id identifies this subscription; close with nmp_app_close_interest.
void olas_open_photo_feed(void* app, uint8_t contact_list_only, const char* consumer_id);

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
/// from a finished Blossom upload. This is the single canonical picture-post
/// publish entry point shared by both platforms.
///
/// blossom_result_json — the `result_json` terminal from nmp.blossom.upload
///   (a BUD-02 descriptor with at least `url` and `sha256`). Required.
/// caption — kind:20 content (NULL → empty).
/// alt     — accessibility alt text for the imeta tag (NULL → omitted).
/// dim     — pixel dimensions "WxH" for the imeta tag (NULL → omitted).
///
/// Returns a JSON string suitable for:
///   nmp_app_dispatch_action(app, "nmp.publish", <returned_json>)
/// The kernel's nmp.publish action constructs and signs the kind:20 — no event
/// JSON or signing happens natively. Returned pointer must be freed with
/// nmp_free_string.
/// geohash — 4-char NIP-52 "g" tag value (NULL → omitted). EXIF GPS is always
/// stripped in the native encoding step; this tag is added only when the user
/// explicitly enables location in the composer.
char* olas_picture_post_publish_json(const char* blossom_result_json, const char* caption, const char* alt, const char* dim, const char* geohash);

// ── New Olas FFI helpers ──────────────────────────────────────────────────────

// Event decoders (caller must free with nmp_free_string)
char* olas_decode_kind20_event_json(const char* event_json);
char* olas_decode_kind0_event_json(const char* event_json);

// Bolt11 parsing (returns msats or -1 on error; no memory to free)
long long olas_bolt11_amount_msats(const char* bolt11);

// Geohash (caller must free with nmp_free_string)
char* olas_compute_geohash(double lat, double lon, int precision);

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
