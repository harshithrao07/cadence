# catalog-service

Owns the music catalog: artists, records (albums/EPs/singles), songs, genres. Handles admin-side uploads to S3 (cover art + audio), public read APIs for browsing, social actions like follow/unfollow, and search/discover.

## Responsibilities

- CRUD for `Artist`, `Record`, `Song`, `Genre`
- Follow / unfollow artists; expose follower lists
- S3 upload + signed URL generation for cover art and audio files
- Search across artists/records/songs and a "discover" feed
- Publish `catalog.record-created` events for notification-service to fan out follower emails
- Provide internal endpoints (`/internal/**`) used by Feign clients in other services

## Architecture

```mermaid
flowchart TB
    gw[gateway-service] --> catalog[catalog-service<br/>:8084]

    catalog --> ac[ArtistController]
    catalog --> rc[RecordController]
    catalog --> sc[SongController]
    catalog --> gc[GenreController]
    catalog --> fc[FilesController<br/>S3 upload/download]
    catalog --> dc[DiscoverController<br/>search/discover]
    catalog --> ic[InternalController]

    ac --> as[ArtistService]
    rc --> rs[RecordService]
    sc --> ss[SongService]
    gc --> gs[GenreService]
    fc --> aws[AwsService]

    as --> ar[ArtistRepository]
    as --> jdbc{{JdbcTemplate}}
    rs --> rr[RecordRepository]
    rs --> rcp[RecordCreatedProducer]
    ss --> sr[SongRepository]
    gs --> gr[GenreRepository]

    ar --> mysql[(MySQL<br/>artist, record,<br/>song, genre,<br/>+ join tables)]
    rr --> mysql
    sr --> mysql
    gr --> mysql
    jdbc -.cross-service reads.-> mysql

    aws --> s3[(AWS S3<br/>cover art<br/>audio files)]

    rcp --> kafka[[catalog.record-created topic]]
    kafka -.consumed.-> notif[notification-service]
```

## Follows and the User Replica

catalog-service **owns `artist_following`** (follow / unfollow / isFollowing / followers / follower counts).
auth-service reads a user's followed artists through `GET /internal/users/{userId}/followed-artists`.

User fields catalog needs (follower names and avatars, release-notification emails) come from **`user_replica`**,
a read-only copy kept in sync from auth-service's `auth.user-updated` events (`UserUpdatedConsumer`; older
snapshots are ignored). catalog never reads auth-service's `users` table. To rebuild the replica, have auth
republish every user: `docker exec cadence-auth curl -X POST localhost:8085/internal/users/republish`.

## File Uploads

`POST /api/v1/files` (multipart, parts named `"table column id"`) and `POST /api/v1/files/presigned-url` accept
only the targets in `UploadTarget`:

| Target | Who may modify | Row lives in | How the row is updated |
|---|---|---|---|
| `song song_url`, `record cover_url`, `artist profile_url` | admins | catalog | directly |
| `users profile_url` | the user themself (or an admin) | auth-service | `catalog.media-updated` event |
| `playlist cover_url` | the playlist owner (or an admin) | playlist-service | `catalog.media-updated` event |

Permission is checked before anything is stored (playlist ownership via `GET /internal/playlists/{id}/owner` on
playlist-service, denied if it is unreachable), and the owning service checks again before applying the event.

## Record Creation Flow

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Admin client
    participant G as gateway
    participant RC as RecordController
    participant RS as RecordService
    participant DB as MySQL
    participant Aws as AwsService → S3
    participant K as Kafka

    Admin->>G: POST /api/v1/record/upsert<br/>(multipart: metadata + cover image)
    G->>RC: forward (admin-only)
    RC->>RS: upsert(...)
    RS->>Aws: upload cover image to S3
    Aws-->>RS: cover URL
    RS->>DB: save Record + linked Songs (in @Transactional)
    DB-->>RS: savedRecord
    RS->>DB: query follower emails<br/>(via JOIN on artist_following + user_replica)
    DB-->>RS: List<followerEmails>
    RS->>K: publish RecordCreatedEvent<br/>{recordId, title, artists, coverUrl, followerEmails}
    RS-->>RC: 201 + recordId
    RC-->>G: response
    G-->>Admin: response

    Note over K: notification-service consumes →<br/>sends one HTML release email<br/>per follower
```

## REST API

All public routes are under `/api/v1/` and reachable through the gateway.

### Artists (`/api/v1/artist`)

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/upsert` | ADMIN | Create or update artist + profile image |
| DELETE | `/delete/{artistId}` | ADMIN | Delete artist + cascade cleanup of join tables + S3 image |
| GET | `/all?page=&size=&key=` | public | Paginated artist listing with optional name filter |
| GET | `/{artistId}` | public | Artist profile + recent records + popular songs + follower count |
| POST | `/{artistId}/follow` | user | Add to current user's follow list (ordered) |
| POST | `/{artistId}/unfollow` | user | Remove from follow list |
| GET | `/{artistId}/isFollowing` | user | Boolean check |
| GET | `/{artistId}/followers` | public | Follower list (paginated) |

### Records (`/api/v1/record`)

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/upsert` | ADMIN | Create or update record + songs + cover art |
| DELETE | `/delete/{recordId}` | ADMIN | Delete record |
| GET | `/all?artistId=` | public | All records by an artist, newest first |
| GET | `/{recordId}` | public | Record detail with songs |

### Songs (`/api/v1/song`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/all?recordId=` | All songs in a record (ordered) |
| GET | `/{songId}` | Song detail |

### Genres (`/api/v1/genre`)

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/add` | ADMIN | Create genre |
| GET | `/all?page=&size=&key=` | public | Paginated genre listing with name filter |

### Files & Discovery

| Method | Path | Purpose |
|---|---|---|
| `/api/v1/files/**` | `FilesController` | S3 upload (admin) + signed URL retrieval |
| GET | `/api/v1/search?key=` | Cross-entity search (artists + records + songs + playlists) |
| GET | `/api/v1/discover` | Recommendation feed: popular artists, new releases, suggested by liked genres |

### Internal (`/internal/**`)

Used by Feign clients in other services (catalog preview lookups). Protected by `InternalTrafficFilter` (gateway-secret header).

## Persistence Model

```mermaid
erDiagram
    ARTIST ||--o{ ARTIST_RECORDS : "in"
    RECORD ||--o{ ARTIST_RECORDS : "by"
    RECORD ||--o{ SONG : "contains"
    SONG ||--o{ ARTIST_CREATED_SONGS : "by"
    ARTIST ||--o{ ARTIST_CREATED_SONGS : "creator"
    SONG ||--o{ SONG_GENRE : "tagged"
    GENRE ||--o{ SONG_GENRE : "tags"
    ARTIST ||--o{ ARTIST_FOLLOWING : "followed by"
    USERS ||--o{ ARTIST_FOLLOWING : "follows"

    ARTIST {
        string id PK
        string name UK
        string profile_url
        string description
    }
    RECORD {
        string id PK
        string title
        long release_timestamp
        string cover_url
        string record_type "ALBUM | EP | SINGLE"
    }
    SONG {
        string id PK
        string title
        int total_duration
        string record_id FK
    }
    GENRE {
        string id PK
        string type UK
    }
    ARTIST_FOLLOWING {
        string user_id
        string artist_id
        int follow_order
    }
    USERS {
        string id PK "owned by auth-service"
    }
```

## Eventing

| Direction | Topic | Event | When |
|---|---|---|---|
| Produces | `catalog.record-created` | `RecordCreatedEvent { recordId, recordTitle, artists, coverUrl, followerEmails }` | After a record is saved + commit |

The producer pre-fetches follower emails synchronously (so the consumer doesn't have to call back into catalog-service). Topic auto-created via `NewTopic` bean (3 partitions, 1 replica).

## Cross-Cutting Concerns

- **Outbound Feign calls** (`PlaylistClient` for global search, `StreamingStatsClient` for the discover feed) go through `FeignClientConfig.gatewaySecretInterceptor` which stamps `X-Gateway-Secret` on every request. Without it, the destination's `InternalTrafficFilter` would 403 the call.
- **Resilience4j circuit breakers** wrap both Feign clients. `PlaylistClientFallback` returns an empty `ApiResponseDTO` so search still returns artists/records/songs when `playlist-service` is down. `StreamingStatsClientFallback` returns empty trending/recently-played/top-songs sections so the discover feed still renders the popular-artists and new-releases sections when `streaming-service` is down.
- **`/actuator/refresh` rebinds `gateway.secret`** via `GatewaySecretProperties` for hot rotation without a restart.

## Configuration

Local `application.properties`:

```properties
spring.application.name=catalog-service
server.port=8084
spring.config.import=optional:file:../env.properties,optional:configserver:http://localhost:8888
```

From `centralconfigs/catalog-service/catalog-service.properties`:

- `spring.datasource.*` — MySQL
- `spring.jpa.hibernate.ddl-auto=update`
- `cloud.aws.credentials.*`, `cloud.aws.region.static`, `cloud.aws.s3.bucket` — S3 client
- `spring.servlet.multipart.*` — 10 MB upload cap
- `server.tomcat.max-swallow-size=-1` — needed for large multipart uploads
- `spring.kafka.producer.*` + `spring.kafka.admin.auto-create=true`

## Testing

| Test | What it covers |
|---|---|
| `ArtistServiceTest` | Follow/unfollow logic, error paths (mocked JdbcTemplate) |
| `GenreServiceTest`, `SongServiceTest`, `RecordServiceTest` | Service-level logic with mocked repositories |
| `ArtistControllerTest`, `GenreControllerTest`, `SongControllerTest`, `RecordControllerTest` | `@WebMvcTest` slices |
| `InternalTrafficFilterTest` | Gateway-secret enforcement |
| `ArtistServiceIT` | Full integration with real MySQL — follow/unfollow happy path + edge cases (already-following, missing user, missing artist, follow-order increment) |
| `RecordKafkaIT` | Real Kafka container — `catalog.record-created` publish + JSON round-trip |

`ArtistService` mixes JPA and raw `JdbcTemplate`; `artist_following` and `user_replica` are mapped as entities, so the test MySQL container gets them from `ddl-auto=create-drop` and tests seed `user_replica` directly.

```bash
cd catalog-service && ./mvnw test
```

## Local Development

```bash
cd catalog-service
./mvnw spring-boot:run
```

Required env:

- `DATASOURCE_*`, `KAFKA_URL`
- `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_REGION`, `AWS_S3_BUCKET` — needed for FilesController to work; if you skip these, S3 uploads will fail but other endpoints still work.

## Boot Order

discovery-service → config-server → catalog-service. Doesn't depend on auth-service for startup; `user_replica` fills as auth-service publishes `auth.user-updated` events.
