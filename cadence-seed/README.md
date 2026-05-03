# cadence-seed

A standalone Node.js script that bulk-populates the Cadence database with realistic fake data — users, genres, artists with real Deezer profile images, records with real album covers, songs with real Jamendo audio URLs, playlists, follows, likes, and play history.

Bypasses the microservices entirely and writes directly to MySQL via `mysql2`. Use it once after spinning up the backend stack so the frontend has something to render instead of empty screens.

> Part of the [Cadence](../) monorepo. See the [root README](../README.md) for backend setup and the [frontend README](../cadence-frontend/README.md) for the UI.

## What Gets Created

| Entity | Count | Source |
|---|---:|---|
| Users | 15 | Faker (password = `password`, BCrypt-hashed) |
| Genres | 15 | Hardcoded list (Pop, Rock, Hip-Hop, ...) |
| Artists | 25 | Faker names + real Deezer profile images |
| Records | 20 | Faker album titles + real Deezer cover art |
| Songs | ~7-12 per record (~6 for EPs) | Real Jamendo tracks (free CC-licensed audio) when found, faker fallback otherwise |
| Playlists | 20 public + 1 "Liked Songs" per user | Faker names, faker cover images |
| Follows | 1-5 artists per user | Random |
| Liked playlists | 1-10 per user | Random |
| Play history | 10-30 songs per user | Random play counts (1-1000 each) |

All images are downloaded from Deezer/Jamendo and uploaded to your S3 bucket so the URLs the app serves point at your bucket, not at external CDNs.

## Architecture

```mermaid
flowchart LR
    seed[seed.js<br/>Node 20+]
    seed -->|raw SQL inserts| mysql[(MySQL :3306<br/>cadence DB)]
    seed -->|fetch artist images| deezer[Deezer API]
    seed -->|fetch album covers| deezer
    seed -->|fetch audio tracks| jamendo[Jamendo API]
    seed -->|upload images| s3[(AWS S3)]
    backend[Backend services] -.read.- mysql
    backend -.serve| browser([Browser])
```

The seed runs once and exits. The backend services then serve the seeded data normally — they don't know or care that it was bulk-loaded.

## Prerequisites

- Node 20+
- `npm install` already run (script uses ESM imports)
- The Cadence backend's MySQL database **must exist and be schema-initialized**. The seed only inserts rows — it doesn't run migrations. Boot at least one backend service that uses JPA (auth-service, catalog-service, playlist-service, or streaming-service) once with `spring.jpa.hibernate.ddl-auto=update` so the tables are created before running the seed.
- A Jamendo client ID (free; sign up at <https://devportal.jamendo.com/>)
- An AWS S3 bucket with write access (or skip — see Configuration)

## Configuration

Create `.env` in this folder:

```env
# MySQL (matches docker compose defaults)
DB_HOST=localhost
DB_PORT=3306
DB_USER=cadence
DB_PASSWORD=cadence
DB_NAME=cadence

# AWS S3 (for hosting downloaded images)
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret
AWS_S3_BUCKET_NAME=your-bucket

# Jamendo (free tracks for songs)
JAMENDO_CLIENT_ID=your_client_id
```

**If you skip S3 or AWS credentials**, image downloads still happen but `uploadToS3` falls back to returning the original Deezer/Jamendo URL. The frontend will still render images — they just come from external CDNs instead of your bucket.

**If you skip Jamendo**, songs still get inserted with a hardcoded fallback streaming URL (a real Jamendo track), so audio playback works for testing — every song will just play the same audio.

## Running

```bash
cd cadence-seed
npm install
node seed.js
```

What you'll see:

```
🚀 Seeding database...
🧹 Truncating tables...
Downloading and uploading image for artist/...
Downloading and uploading image for record/...
✅ Database seeded successfully
```

Run time: ~2-5 minutes depending on Deezer/Jamendo API latency and S3 upload throughput. Each artist + record + playlist makes one external API call and one S3 upload.

## What Gets Truncated

The script's first step wipes these tables (in dependency order, with FK checks off):

- `song_genre`, `liked_playlists`, `playlist_songs`, `play_history`,
- `artist_following`, `artist_created_songs`, `artist_records`,
- `song`, `record`, `artist`, `genre`, `playlist`, `users`

So **running this destroys all existing data**. If you've manually created accounts or playlists through the frontend, they're gone. Use only against a dev database.

## Login After Seeding

All seeded users have password `password` (BCrypt-hashed). Pick any seeded email from the `users` table to log in:

```sql
SELECT email FROM users LIMIT 5;
```

To make one of them an admin (so you can hit `/records/add` etc. from the frontend):

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'someone@example.com';
```

## Customization

Tune the volume by editing the `COUNTS` object at the top of `seed.js`:

```js
const COUNTS = {
  USERS: 15,
  ARTISTS: 25,
  RECORDS: 20,
  SONGS_PER_RECORD: 10,   // upper bound; EPs are 6, albums are 7-12
  PLAYLISTS: 20,
};
```

Bumping artists/records past ~50 each starts hammering the Deezer / Jamendo APIs — both have rate limits. If you see `Failed to fetch image for X: Request failed with status 429`, slow down or batch.

## Schema Assumptions

The script writes to these tables (owned by the corresponding services):

| Table | Owner service | Columns the seed populates |
|---|---|---|
| `users` | auth-service | `id, email, name, password_hash, role, email_verified` |
| `genre` | catalog-service | `id, type` |
| `artist` | catalog-service | `id, name, profile_url, description` |
| `record` | catalog-service | `id, title, release_timestamp, cover_url, record_type` |
| `song` | catalog-service | `id, title, song_url, total_duration, record_id, song_order` |
| `artist_records` | catalog-service | `record_id, artist_id, artist_order` |
| `artist_created_songs` | catalog-service | `song_id, artist_id, artist_order` |
| `song_genre` | catalog-service | `song_id, genre_id` |
| `artist_following` | auth-service join table | `user_id, artist_id, follow_order` |
| `playlist` | playlist-service | `id, name, cover_url, user_id, visibility, is_system, system_type, created_at, updated_at` |
| `playlist_songs` | playlist-service | `playlist_id, song_id, song_order` |
| `liked_playlists` | playlist-service | `user_id, playlist_id, like_order` |
| `play_history` | streaming-service | `user_id, song_id, play_count, created_at, last_played_at` |

If a backend entity adds a new NOT NULL column, the seed will fail with an `Unknown column` or `cannot be null` error. Fix by either making the column nullable in the entity or adding it to the corresponding INSERT in `seed.js`.

## Troubleshooting

- **`ER_ACCESS_DENIED_ERROR`** — `DB_USER`/`DB_PASSWORD` wrong, or the user lacks permissions on `DB_NAME`.
- **`ER_NO_SUCH_TABLE`** — backend hasn't created the schema. Boot one JPA-using service with `ddl-auto=update` first.
- **`ER_BAD_NULL_ERROR: Column '...' cannot be null`** — backend added a NOT NULL column the seed doesn't fill. Add it to the relevant INSERT.
- **`getaddrinfo ENOTFOUND api.deezer.com`** — no internet, or Deezer is down. Script falls back to faker images so it still completes.
- **`Failed to fetch track: Request failed with status 401`** from Jamendo — `JAMENDO_CLIENT_ID` missing or invalid. Songs get the hardcoded fallback URL (everything plays the same audio).
- **All artists/songs end up with default fallback assets** — Deezer/Jamendo APIs blocked or rate-limited from your IP. The seed completes but content is generic.
- **`ER_ROW_IS_REFERENCED_2` during truncate** — A new join table exists in the schema that's not in `truncateAll()`. Add it to the truncate list (in FK-dependency order before its parents).
