# AGENTS.md — Olas Relay

> The Olas relay is based on **Croissant** by fiatjaf — a NIP-29 group relay built on khatru. See `../hl/relay/` for the fuller Croissant implementation to draw from.

**Source reference:** `nak git clone npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6/croissant`

## Tech Stack

- **Language:** Go
- **Framework:** khatru + NIP-29 group logic from Croissant
- **Storage:** MMM MultiMmapManager (embedded event store)
- **Build:** `just` (justfile)

## Setup Commands

```bash
cd relay
go mod download
go run .
```

## File Size

The 300/500 LOC limits in the root `AGENTS.md` apply to Go source files here as well.

## Architecture

The relay is an independent process. It owns its own state and speaks the Nostr wire protocol. Business decisions that belong to the app (e.g. what events the user posts) live in Rust, not here.
