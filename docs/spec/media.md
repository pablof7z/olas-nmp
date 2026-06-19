# Olas — Media & Blossom Spec

## Blossom overview

Blossom is a content-addressed HTTP blob store. Every blob is identified by its SHA-256 hash, served from a URL like `https://server.example/[64-char-hex]`. Two blobs with the same hash are the same content — any server that has it serves the same thing.

Olas uses Blossom for all uploaded media. The motivation: media that exists on many servers is media that persists. If one server goes down, any other with the same hash works.

NIPs involved:
- **BUD-01** — basic blob serving (GET, HEAD, DELETE, list)
- **BUD-02** — auth header via signed kind-24242 events
- **BUD-03** — user server list (kind 10063)

---

## Upload pipeline (detailed)

### Step 1 — Local processing

Before any network call:

1. Read media file from camera roll (full resolution, original format).
2. Apply filter and adjustment stack (client-side GPU, native layer).
3. Re-encode at output settings (see Encoding below).
4. Compute SHA-256 of the encoded bytes in Rust via `nmp-app-olas`.
5. Compute blurhash from a 100×100px downscale of the encoded image.
6. Read `dim` (WxH in pixels) from the encoded output.
7. Detect MIME type from magic bytes (never trust file extension).

All of steps 1–7 run in Rust on a background thread. The UI can proceed (caption editing) during this time.

### Step 2 — Auth event

Build a signed kind-24242 auth event:
```json
{
  "kind": 24242,
  "content": "Upload [hash]",
  "tags": [
    ["t", "upload"],
    ["x", "<sha256-hex>"],
    ["expiration", "<unix-ts + 600>"]
  ]
}
```
Sign via the active signer. Cache the signed event for the duration of the upload (reuse across mirror uploads for the same blob).

### Step 3 — Primary upload

`PUT https://<primary-server>/<sha256-hex>` with:
- `Authorization: Nostr <base64(json(signed-kind-24242))>`
- `Content-Type: <mime-type>`
- Body: raw bytes

Track upload progress via chunked transfer or `Content-Length` + received-bytes callback. Feed a real percentage to the UI.

On 200 or 201: proceed. The returned URL may differ from `<server>/<hash>` — use the URL returned by the server for the `imeta` tag, not the derived one.

On any server error (4xx/5xx): retry up to 2× with exponential backoff (1s, 3s), then surface to user.

On connection loss: pause, resume when connectivity returns (use background URLSession on iOS, WorkManager on Android).

### Step 4 — Mirror uploads

After the primary upload completes, upload in parallel to all other configured servers using the same auth event and bytes. Mirror success/failure is logged but does not block publish. A "Mirrored to N servers" line in the post's detail view (power-user info) counts confirmed mirrors.

### Step 5 — Build imeta tags

```
imeta
  url <resolved-url-from-server>
  x   <sha256-hex>
  m   <mime-type>
  dim <WxH>
  blurhash <blurhash-string>
  alt <alt-text-if-provided>
```

One `imeta` tag group per media item. For multi-image posts, `imeta` tags appear in order matching the image carousel order.

### Step 6 — Publish

Construct kind-20 event:
```json
{
  "kind": 20,
  "content": "<caption>",
  "tags": [
    ["imeta", "url <url>", "x <hash>", "m <mime>", "dim WxH", "blurhash <bh>"],
    ["alt", "<first-image-alt-text-or-caption>"]
  ]
}
```

Sign. Publish to all write relays via `nmp-core`. At least one relay acceptance counts as success. Optimistic: UI shows the post immediately after signing.

---

## Encoding settings

### Images

| Setting | Default | Advanced override |
|---|---|---|
| Format | JPEG | JPEG / WebP / PNG |
| JPEG quality | 92 | 60–100 |
| Max dimension | 2048px (long edge) | Off / 1024 / 1500 / 2048 / 4096 |
| Downscale algorithm | Lanczos | — |
| Strip EXIF | Yes (except orientation) | Keep all |
| Color profile | sRGB | Preserve original |

Filters and adjustments are baked into the encoded JPEG. The original (pre-filter) is never uploaded unless the user selects "Original" filter explicitly.

### Videos

| Setting | Default | Advanced override |
|---|---|---|
| Format | MP4 (H.264 + AAC) | MP4 / MP4 (H.265) |
| Max resolution | 1080p | 480p / 720p / 1080p / Original |
| Target bitrate | 8 Mbps | 2–25 Mbps |
| Frame rate cap | 30 fps | 24 / 30 / 60 fps |
| Audio | 128 kbps AAC | 96 / 128 / 192 kbps |
| Max duration | 10 minutes | No limit |

Compression runs before upload begins. Progress shown as "Processing…" then "Uploading N%."

---

## Read path

### Image loading sequence

1. Check in-memory cache (NSCache / Coil MemoryCache).
2. Check disk cache (local NSFileManager / Coil DiskCache), keyed by URL.
3. Fetch from the URL in the `imeta` tag.
4. On HTTP error: extract hash from the `imeta x` tag and attempt all other known servers for this hash.
5. Verify SHA-256 on receipt. Reject and move to next server on mismatch.
6. Decode and display with blurhash placeholder during step 3–5.

### Blurhash placeholders

Every image shows a blurred placeholder (computed from the `blurhash` imeta tag) while the full image loads. Placeholder matches the target image dimensions — no layout shift. Transition: crossfade 150ms once full image decodes.

### Progressive loading

For large images: decode a low-res thumbnail first (JPEG progressive scan or a 10× downscale pass), display immediately, then swap to full-res without flicker.

### Video loading

Videos do not autoplay until the first frame is decoded. The cover frame (from `imeta`'s `thumb` tag or the first frame) shows immediately. On playback: stream from the URL, buffer 5s ahead. No download-full-before-play.

---

## Server health and fallback

### Health check cadence

On app foreground and on every relay reconnect: background-ping all configured Blossom servers with a HEAD request. Status:
- **Connected** (< 300ms round-trip)
- **Slow** (300ms–2s)
- **Down** (timeout or error after 2 retries)

### Read fallback logic

```
for url in [primary_url, ...mirror_urls]:
    response = fetch(url)
    if response.ok and sha256(response.body) == expected_hash:
        return response.body
    continue
return error("media unavailable")
```

The loop tries every known server for the same hash. Servers are tried in order: the URL in the `imeta` tag first, then any server from the user's kind-10063 list that isn't the same host, then any previously seen Blossom URL for the same hash observed from other users' profiles.

### Write fallback

If the primary server is Down at publish time: try the next server in the list (server 2 becomes primary for this upload). Surface a note to the user: "Your primary server is unreachable. Uploading to [next server] instead."

---

## Server management

### User server list (kind 10063)

Kind 10063 is a replaceable event listing the user's preferred Blossom servers in priority order:
```json
{
  "kind": 10063,
  "tags": [
    ["server", "https://blossom.primal.net"],
    ["server", "https://cdn.satellite.earth"]
  ]
}
```

Olas reads this on login to hydrate the server list. Publishes an updated kind-10063 whenever the user changes their server list in settings.

### Curated server directory

A hardcoded (but updatable via app update) list of known public Blossom servers for the "Add server" picker:
- `blossom.primal.net` — "Primal — fast global CDN"
- `cdn.satellite.earth` — "Satellite Earth — community-run"
- `blossom.band` — "Band — music & media focus"
- `files.sovbit.host` — "SovBit — self-sovereign, EU"
- Custom URL entry always available

### Storage browser

Fetches `GET https://<server>/list/<user-pubkey-hex>` (BUD-01 list endpoint). Displays blobs with:
- Thumbnail
- File size
- Upload date (from `created` field)
- SHA-256 hash (truncated, tap to copy full)
- "Delete from server" — sends `DELETE /<hash>` with a fresh kind-24242 auth token, action=delete.

Deletion from server does not delete the Nostr event. The user is warned: "This removes the file from this server. The post may still appear with a broken image on this and other apps."

---

## Security considerations

### Hash verification (critical)

Every blob fetched is verified against the `x` tag before render. A server returning incorrect bytes for a hash either:
- Has a corrupted blob (benign) — skip to next server.
- Is serving a substituted file (adversarial) — skip and flag server as untrusted for this session.

This is the core Blossom security guarantee: URLs lie, hashes don't.

### Auth token scope

Kind-24242 tokens are scoped to a single hash and expire in 10 minutes. Never reuse a token for a different hash. Never send a token over plain HTTP (Blossom servers must use HTTPS; reject any `http://` server URL before attempting upload).

### EXIF stripping

Strip EXIF by default before upload. EXIF can contain GPS location, device serial number, and timestamps the user may not want public. Opt-in to preserve EXIF is in advanced settings, with an explicit privacy warning.

### Inline content checks

Olas does not perform client-side CSAM or illegal content scanning — that responsibility lies with the Blossom servers and relay operators. The app reports posts via NIP-56 report events when the user taps Report.
