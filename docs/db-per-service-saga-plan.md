# Database-per-service + Choreography SAGA — Plan

Status: **in progress** on branch `feat/db-per-service-saga` — Phases 0–1 done.

## Why

All services currently share one MySQL database (`cadenceDB`), and the boundaries leak:

- catalog-service reads/writes auth-service's `users` and `artist_following` tables via raw `JdbcTemplate` SQL (follow/unfollow, followers list, follower counts, new-release follower emails).
- auth-service maps catalog-service's `artist` table as its own entity.
- Events (`user_created`, `email_verification`, `record_created`) are published inside the DB transaction with no outbox, so a Kafka outage or a rollback desyncs services.

## Decisions

| Topic | Decision |
|---|---|
| Saga style | **Choreography** over Kafka, no orchestrator |
| Follow ownership | `artist_following` moves to **catalog-service**; catalog keeps a `user_replica` |
| How `user_replica` syncs | **Domain events via transactional outbox** (not Feign, not CDC on the `users` table) |
| DB layout | **One MySQL, 4 schemas** (`auth_db`, `catalog_db`, `playlist_db`, `streaming_db`), one DB user per service granted only its own schema |
| Existing data | **Drop and reseed** with an updated `cadence-seed` |
| User deletion (Saga D) | Included as the **last phase**; event contract defined early |
| Outbox relay | **Polling** relay; columns laid out Debezium-outbox-router-compatible so CDC can replace it later |
| Event classes | Shared **`cadence-events`** Maven module |
| Artist / record deletes | **Soft delete** (`DELETING`), hard delete after playlist + streaming confirm clean-up |

### Why choreography

Both sagas are short: registration has 2 participants and 1 compensation; catalog deletes are a forward-only fan-out. Kafka and choreographed events already exist (`user_created` → playlist). Revisit orchestration only if a ≥4-step flow with real rollbacks appears (e.g. paid subscriptions) — and then only for that flow.

Visibility mitigations: every event carries a `sagaId`, it is put in the logging MDC, and the initiating service tracks the outcome (e.g. `users.status`).

### Why catalog owns follows

Every follow operation (follow/unfollow, counts, followers list, release emails, cleanup on artist delete) becomes local to catalog. The only replicated data is a few rarely-changing user fields. The alternative (auth owns follows) would need an artist replica in auth *plus* sync calls back from catalog on the hottest read path (artist page).

### Why outbox events for `user_replica`

- Feign-on-demand: follow/followers break when auth is down, N calls on hot paths.
- CDC on `users`: couples catalog to auth's table schema and leaks fields like `password_hash`.
- Outbox events: auth controls the contract, nothing is lost, catalog works when auth is down, and it's the same machinery the sagas need anyway.

## Target ownership

| Service | Schema | Tables |
|---|---|---|
| auth-service | `auth_db` | `users` (+ `status`: `PENDING`/`ACTIVE`/`FAILED`/`DELETING`), `email_verification_token`, `outbox`, `processed_events`, `user_deletion_saga` |
| catalog-service | `catalog_db` | `artist`, `record`, `song`, `genre`, `artist_records`, `artist_created_songs`, `song_genre`, `artist_following`, `user_replica`, `pending_deletions`, `outbox`, `processed_events` |
| playlist-service | `playlist_db` | `playlist`, `playlist_songs`, `liked_playlists`, `outbox`, `processed_events` |
| streaming-service | `streaming_db` | `play_history`, `outbox`, `processed_events` |
| notification-service | — | (Kafka consumer only) |

**Rule:** no service reads another service's tables. Cross-service data comes from a local replica (events), a Feign call (read-time enrichment only), or a saga (multi-service writes).

## Event catalog

Every event is wrapped in an envelope: `eventId` (UUID), `sagaId`, `type`, `version`, `occurredAt`, `payload`.

| Topic | Producer | Consumers | Purpose |
|---|---|---|---|
| `user.registered` | auth | playlist | Saga A step 1 |
| `playlist.liked-songs-created` | playlist | auth | Saga A success |
| `playlist.liked-songs-failed` | playlist | auth | Saga A compensation |
| `user.activated` | auth | catalog, notification | Replica insert; verify / welcome mail |
| `user.updated` | auth | catalog | Replica update |
| `user.deletion-requested` | auth | playlist, catalog, streaming | Saga D |
| `user.data-purged` | playlist, catalog, streaming | auth | Saga D confirmations |
| `catalog.record-deleted` / `catalog.artist-deleted` | catalog | playlist, streaming | Saga B (carries `songIds`) |
| `catalog.songs-purged` | playlist, streaming | catalog | Saga B confirmations |
| `auth.user-created` | auth | playlist | Existing flow on the outbox; replaced by `user.registered` in Phase 3 |
| `auth.email-verification` | auth | notification | Existing flow, moved onto the outbox |
| `catalog.record-created` | catalog | notification | Existing flow, moved onto the outbox |

Every topic gets a `.DLT` dead-letter topic.

## Sagas

### Saga A — registration

```
auth: create user (PENDING) + outbox user.registered
  → playlist: create "Liked Songs" (idempotent) + outbox liked-songs-created | liked-songs-failed
  → auth: user ACTIVE + outbox user.activated   | user FAILED (compensation)
       → catalog: insert user_replica
       → notification: verification / welcome mail
```

- Login / JWT refused unless `ACTIVE`. Both email signup and OAuth.
- Timeout sweeper: `PENDING` older than 5 minutes → `FAILED`.

### Saga B — artist / record delete

```
catalog: status=DELETING (hidden from reads) + outbox record-deleted/artist-deleted {songIds}
  → playlist: remove songIds from playlist_songs  → songs-purged
  → streaming: delete play_history for songIds     → songs-purged
  → catalog: both acks → hard delete rows, join tables, follows (artist), S3 objects
```

Forward-only: consumers retry until they succeed; poison messages go to the DLT.

### Saga C — profile replication

`user.updated` → catalog updates `user_replica`. Plain replication.

### Saga D — user deletion (last phase)

```
auth: DELETE /api/v1/users/me → status DELETING, revoke tokens, outbox user.deletion-requested
  → playlist: own playlists, liked_playlists, likes on others'  → user.data-purged
  → catalog: artist_following rows, user_replica row            → user.data-purged
  → streaming: play_history                                     → user.data-purged
  → auth: 3 acks → hard-delete / anonymize user
```

Forward-only, no compensation.

### Not sagas

- Follow / unfollow: local to catalog after Phase 2.
- Add song to playlist: local write after a Feign existence check; Saga B cleans up later deletions.

## Phases

### Phase 0 — shared module and build changes

- [x] `cadence-events` module: event records, envelope, topic constants
- [x] Root aggregator `pom.xml` + Maven wrapper at repo root
- [x] Docker build context = repo root for services that depend on `cadence-events`; root `.dockerignore`
- [x] `compose.yaml`, `ci.yml`, `run-all-tests.sh` updated
- [x] Delete duplicated event classes and `Topics` copies in auth, catalog, playlist and notification

### Phase 1 — reliable messaging (no behaviour change)

Implemented as a shared Spring Boot auto-configuration library, **`cadence-messaging`**, rather than per-service code:

- [x] `outbox` table (`id`, `event_id`, `saga_id`, `aggregate_type`, `aggregate_id`, `type`, `topic`, `payload`, `created_at`, `sent_at`, `failed_at`, `last_error`) — entity in the library, created by each JPA service's `ddl-auto`
- [x] `OutboxPublisher` (`Propagation.MANDATORY`; a failure marks the caller's transaction rollback-only even if the caller swallows it)
- [x] `PollingOutboxRelay`: own single-thread scheduler, 500 ms, `FOR UPDATE SKIP LOCKED`, batch 100; hourly cleanup of rows sent > 7 days ago
  - retriable send failure (broker down, missing topic) blocks only that topic for the batch, preserving its order while other topics flow; retried forever
  - permanent Kafka error (invalid topic, record too large) parks the row (`failed_at`, `last_error`); set `failed_at = NULL` to retry
  - `max.block.ms` capped at the 10 s send timeout so a missing topic can't stall the relay for 60 s per attempt
- [x] `processed_events(handler, event_id)` + `IdempotentEventHandler`, written in the consumer's transaction
- [x] `DefaultErrorHandler`: 4 exponential retries (1 s → 10 s), then `<topic>.DLT`; `EventDecodingException` skips retries
- [x] Existing producers write to the outbox; every topic now carries `EventEnvelope` JSON as a string value
- [x] `sagaId` / `eventId` in the logging MDC (`logging.pattern.level` in the global config)

Decisions made while implementing:

- **Topics renamed** with the format change so old and new formats never share a topic: `user_created` → `auth.user-created`, `email_verification` → `auth.email-verification`, `record_created` → `catalog.record-created`. Convention: `<producing-service>.<event>`. (A first attempt used `user.created`, which Kafka rejects next to an existing `user_created`: `.` and `_` collide in topic names.)
- **`EventCodec` uses its own Jackson mapper**, not the service's, so the wire contract can't drift with one service's Jackson config; it ignores unknown fields for forward compatibility.
- **notification-service has no database**, so it decodes envelopes and joins the saga context but has no inbox: a redelivered event can send a duplicate email.
- **streaming-service** gets the library in Phase 4, when it first produces / consumes events.

### Phase 2 — follows move to catalog (shared DB still)

- [ ] `UserReplica` entity + consumer for `user.activated` / `user.updated`
- [ ] Remove every `users` read from catalog (`ArtistService.userExists`, `getArtistFollowers`, `GenericService.userExists`, `RecordService.getFollowerEmails`)
- [ ] Catalog `GET /internal/users/{id}/followed-artists`
- [ ] auth: remove `Artist` entity and `User.artistFollowing` mapping; profile uses a Feign `CatalogClient` (fallback: empty list)
- [ ] auth: publish `user.updated` on profile update

### Phase 3 — Saga A (registration)

- [ ] `users.status`; email signup + OAuth create `PENDING` and emit `user.registered`
- [ ] playlist creates Liked Songs idempotently, replies success / failure
- [ ] auth activates or fails the user; emits `user.activated`
- [ ] Login / JWT gated on `ACTIVE`; frontend "setting up your account" state
- [ ] Timeout sweeper

### Phase 4 — Saga B (catalog deletes)

- [ ] `status` (`ACTIVE`/`DELETING`) on `artist`, `record`, `song`; all reads filter `ACTIVE`
- [ ] Delete → `DELETING` + `record-deleted` / `artist-deleted`
- [ ] playlist + streaming purge and ack with `songs-purged`
- [ ] `pending_deletions` tracks acks → hard delete + S3 cleanup

### Phase 5 — physical split

- [ ] `docker/mysql/init.sql`: 4 schemas + 4 users with per-schema grants
- [ ] Per-service datasource URL / user / password in config-server and `env.properties`
- [ ] `compose.yaml` mounts the init script, passes per-service credentials
- [ ] `cadence-seed` writes to 4 schemas, fills `user_replica`, follows into `catalog_db`, `users.status='ACTIVE'`; README diagram updated
- [ ] Drop `cadenceDB`, reseed
- [ ] Verify a cross-schema query (catalog → `auth_db.users`) fails with a permission error

### Phase 6 — Saga D (user deletion)

- [ ] `DELETE /api/v1/users/me`, `DELETING` status, token revocation
- [ ] Purge consumers in playlist, catalog, streaming; `user.data-purged` acks
- [ ] `user_deletion_saga` ack tracking → hard delete / anonymize
- [ ] Frontend delete-account flow with confirmation

### Testing (every phase)

- [ ] Embedded Kafka / Testcontainers tests per saga: success, compensation or timeout, duplicate event ignored
- [ ] Outbox relay tests: crash after send, two relay instances running concurrently
- [ ] Re-run the `/discover` load test after the split
- [ ] Update `TESTING.md` and the README architecture section

## Risks

- Phase 0 changes the build (CI, Docker) — land it as its own change.
- Phases 1–4 run on the shared DB so the system keeps working; Phase 5 should be mostly config, and the grants check catches anything missed.
- Saga A changes login behaviour — test both email and OAuth signup.
