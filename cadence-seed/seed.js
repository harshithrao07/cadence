import mysql from "mysql2/promise";
import { fa, faker } from "@faker-js/faker";
import { v4 as uuid } from "uuid";
import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import axios from "axios";
import dotenv from "dotenv";
import bcrypt from "bcrypt";

dotenv.config();

/* ===============================
   CONFIG
   =============================== */

const DB_CONFIG = {
  host: process.env.DB_HOST || "localhost",
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER || "root",
  password: process.env.DB_PASSWORD || "password",
  database: process.env.DB_NAME || "cadenceDB",
};

const s3Client = new S3Client({
  region: process.env.AWS_REGION,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  },
});

const S3_BUCKET_NAME = process.env.AWS_S3_BUCKET_NAME;

const COUNTS = {
  USERS: 15,
  ARTISTS: 25,
  RECORDS: 20,
  SONGS_PER_RECORD: 10,
  PLAYLISTS: 20,
};

/* ===============================
   DB CONNECTION
   =============================== */

const db = await mysql.createConnection(DB_CONFIG);

/* ===============================
   HELPERS
   =============================== */

const pick = (arr, min = 1, max = 2) =>
  faker.helpers.arrayElements(arr, faker.number.int({ min, max }));

async function getRealArtistImage(name) {
  try {
    const response = await axios.get(
      `https://api.deezer.com/search/artist?q=${encodeURIComponent(name)}`
    );
    if (response.data && response.data.data && response.data.data.length > 0) {
      return response.data.data[0].picture_xl || response.data.data[0].picture_big;
    }
  } catch (error) {
    console.warn(`Failed to fetch image for ${name}:`, error.message);
  }
  return faker.image.avatar(); // Fallback
}

async function getRealAlbumCover(title) {
  try {
    const response = await axios.get(
      `https://api.deezer.com/search/album?q=${encodeURIComponent(title)}`
    );
    if (response.data && response.data.data && response.data.data.length > 0) {
      return response.data.data[0].cover_xl || response.data.data[0].cover_big;
    }
  } catch (error) {
    console.warn(`Failed to fetch cover for ${title}:`, error.message);
  }
  return faker.image.url(); // Fallback
}

async function getJamendoTrack(searchQuery) {
  try {
    const response = await axios.get(
      `https://api.jamendo.com/v3.0/tracks/`,
      {
        params: {
          client_id: process.env.JAMENDO_CLIENT_ID,
          format: 'json',
          limit: 1,
          search: searchQuery,
          audioformat: 'mp32' // Higher quality
        }
      }
    );
    
    if (response.data?.results?.[0]) {
      const track = response.data.results[0];
      return {
        title: track.name,
        artist: track.artist_name,
        duration: track.duration,
        audioUrl: track.audio, // Streaming URL
        downloadUrl: track.audiodownload, // Download URL
        imageUrl: track.album_image
      };
    }
  } catch (error) {
    console.warn(`Failed to fetch track: ${error.message}`);
  }
  return null;
}

async function uploadToS3(url, tableName, columnName, id) {
  try {
    if (!S3_BUCKET_NAME) return url; // Fallback if no bucket configured

    console.log(`Downloading and uploading image for ${tableName}/${id}...`);
    const response = await axios.get(url, { responseType: "arraybuffer" });
    const buffer = Buffer.from(response.data, "binary");
    const key = `${tableName}/${columnName}/${id}`;

    await s3Client.send(
      new PutObjectCommand({
        Bucket: S3_BUCKET_NAME,
        Key: key,
        Body: buffer,
        ContentType: response.headers["content-type"],
      })
    );

    return `https://${S3_BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${key}`;
  } catch (e) {
    console.error(`Failed to upload to S3: ${e.message}`);
    return url; // Fallback to original URL on failure
  }
}

/* ===============================
   CLEAN DATABASE
   =============================== */

async function truncateAll() {
  console.log("🧹 Truncating tables...");
  await db.query("SET FOREIGN_KEY_CHECKS = 0");
  await db.query("TRUNCATE song_genre");
  await db.query("TRUNCATE liked_playlists");
  await db.query("TRUNCATE playlist_songs");
  await db.query("TRUNCATE play_history");
  await db.query("TRUNCATE artist_following");
  await db.query("TRUNCATE artist_created_songs");
  await db.query("TRUNCATE artist_records");
  await db.query("TRUNCATE email_verification_token");
  await db.query("TRUNCATE song");
  await db.query("TRUNCATE record");
  await db.query("TRUNCATE artist");
  await db.query("TRUNCATE genre");
  await db.query("TRUNCATE playlist");
  await db.query("TRUNCATE users");
  await db.query("SET FOREIGN_KEY_CHECKS = 1");
}

/* ===============================
   USERS
   =============================== */

async function seedUsers() {
  const users = [];
  const passwordHash = await bcrypt.hash("password", 10);

  for (let i = 0; i < COUNTS.USERS; i++) {
    users.push([
      uuid(),
      faker.internet.email(),
      faker.person.fullName(),
      passwordHash,
      "USER",
      faker.datatype.boolean(),
    ]);
  }

  await db.query(
    `INSERT INTO users (id, email, name, password_hash, role, email_verified) VALUES ?`,
    [users]
  );

  return users.map(u => u[0]);
}

async function seedGenres() {
  const genreTypes = [
    "Pop",
    "Rock",
    "Hip-Hop",
    "R&B",
    "Electronic",
    "Jazz",
    "Classical",
    "Reggae",
    "Country",
    "Folk",
    "Indie",
    "Metal",
    "Punk",
    "Soul",
    "Blues",
  ];

  const genreRows = genreTypes.map(type => [uuid(), type]);

  await db.query(
    `INSERT INTO genre (id, type) VALUES ?`,
    [genreRows]
  );

  return genreRows.map(row => row[0]);
}

/* ===============================
   ARTISTS
   =============================== */

async function seedArtists() {
  const uniqueNames = new Set();
  while (uniqueNames.size < COUNTS.ARTISTS) {
    uniqueNames.add(faker.music.artist());
  }

  const promises = Array.from(uniqueNames).map(artistName => {
    const id = uuid();
    return (async () => {
      const imageUrl = await getRealArtistImage(artistName);
      const s3Url = await uploadToS3(imageUrl, "artist", "profile_url", id);
      
      return [id, artistName, s3Url, faker.lorem.sentence()];
    })();
  });

  const artistRows = await Promise.all(promises);

  await db.query(
    `INSERT INTO artist (id, name, profile_url, description)
     VALUES ?`,
    [artistRows]
  );

  return artistRows.map((a) => a[0]);
}

/* ===============================
   RECORDS + ARTISTS
   =============================== */

async function seedRecords(artistIds) {
  const promises = [];
  const recordTypes = ["ALBUM", "EP"];
  const uniqueTitles = new Set();

  for (let i = 0; i < COUNTS.RECORDS; i++) {
    const id = uuid();
    let title = faker.music.album();
    
    // Ensure unique record title
    while (uniqueTitles.has(title)) {
      title = faker.music.album();
    }
    uniqueTitles.add(title);

    const recordType = faker.helpers.arrayElement(recordTypes);

    promises.push(
      (async () => {
        const coverUrl = await getRealAlbumCover(title);
        const s3Url = await uploadToS3(coverUrl, "record", "cover_url", id);

        return [
          id,
          title,
          Date.now(),
          s3Url,
          recordType,
        ];
      })()
    );
  }

  const recordRows = await Promise.all(promises);

  await db.query(
    `INSERT INTO record (id, title, release_timestamp, cover_url, record_type)
     VALUES ?`,
    [recordRows]
  );

  const recordsWithArtists = [];

  for (const record of recordRows) {
    const artists = pick(artistIds);
    recordsWithArtists.push({ record, artists });

    for (let i = 0; i < artists.length; i++) {
      await db.query(
        `INSERT INTO artist_records (record_id, artist_id, artist_order)
         VALUES (?, ?, ?)`,
        [record[0], artists[i], i]
      );
    }
  }

  return recordsWithArtists;
}

/* ===============================
   SONGS + ARTISTS
   =============================== */

async function seedSongs(recordsWithMeta, artistIds, genreIds) {
  const songIds = [];
  const uniqueSongTitles = new Set();

  for (const { record, artists: recordArtists } of recordsWithMeta) {
    const [recordId, recordTitle, , , recordType] = record;
    
    let songCount;
    if (recordType === "EP") songCount = 6;
    else songCount = faker.number.int({ min: 7, max: 12 });
    
    const recordSongPromises = [];

    for (let i = 0; i < songCount; i++) {
      recordSongPromises.push((async () => {
        const songId = uuid();
        
        // Try to get real track from Jamendo
        const jamendoTrack = await getJamendoTrack(recordTitle);
        
        let title, songUrl, duration;
        
        if (jamendoTrack) {
          title = jamendoTrack.title;
          duration = jamendoTrack.duration;
          // Use the direct audio URL (no need to upload to S3 since Jamendo provides streaming URLs)
          songUrl = jamendoTrack.audioUrl;
        } else {
          // Fallback to faker with default URL
          title = faker.music.songName();
          duration = faker.number.int({ min: 120, max: 360 });
          songUrl = "https://prod-1.storage.jamendo.com/?trackid=1342155&format=mp32&from=aR7qSx0mN24ZRT%2B9UZbutA%3D%3D%7CS4hTccICgkCLFMytOGM7DA%3D%3D";
        }

        // Ensure unique song title (append UUID substring if duplicate)
        if (uniqueSongTitles.has(title)) {
          title = `${title} (${uuid().substring(0, 8)})`;
        }
        uniqueSongTitles.add(title);

        return { songId, title, songUrl, duration };
      })());
    }

    const songsData = await Promise.all(recordSongPromises);

    for (let i = 0; i < songsData.length; i++) {
      const song = songsData[i];
      songIds.push(song.songId);

      await db.query(
        `INSERT INTO song (id, title, song_url, total_duration, record_id, song_order)
         VALUES (?, ?, ?, ?, ?, ?)`,
        [
          song.songId,
          song.title,
          song.songUrl,
          song.duration,
          recordId,
          i
        ]
      );

      const songSpecificArtists = pick(artistIds, 0, 1);
      const finalArtists = [...recordArtists];

      for (const artistId of songSpecificArtists) {
        if (!finalArtists.includes(artistId)) {
          finalArtists.push(artistId);
        }
      }

      for (let j = 0; j < finalArtists.length; j++) {
        await db.query(
          `INSERT INTO artist_created_songs (song_id, artist_id, artist_order)
           VALUES (?, ?, ?)`,
          [song.songId, finalArtists[j], j]
        );
      }

      const genres = pick(genreIds, 1, 3);
      for (const genreId of genres) {
        await db.query(
          `INSERT INTO song_genre (song_id, genre_id)
           VALUES (?, ?)`,
          [song.songId, genreId]
        );
      }
    }
  }

  return songIds;
}

/* ===============================
   ARTIST FOLLOWERS
   =============================== */

async function seedFollowers(userIds, artistIds) {
  for (const userId of userIds) {
    const followed = pick(artistIds, 1, 5);
    for (let i = 0; i < followed.length; i++) {
      await db.query(
        `INSERT IGNORE INTO artist_following (user_id, artist_id, follow_order)
         VALUES (?, ?, ?)`,
        [userId, followed[i], i]
      );
    }
  }
}

/* ===============================
   PLAY HISTORY
   =============================== */

async function seedPlayHistory(userIds, songIds) {
  for (const userId of userIds) {
    const songs = pick(songIds, 10, 30);
    for (const songId of songs) {
      await db.query(
        `INSERT INTO play_history
         (user_id, song_id, play_count, created_at, last_played_at)
         VALUES (?, ?, ?, NOW(), NOW())`,
        [
          userId,
          songId,
          faker.number.int({ min: 1, max: 1000 }),
        ]
      );
    }
  }
}

async function seedPlaylists(userIds, songIds) {
  const playlistPromises = [];

  for (let i = 0; i < COUNTS.PLAYLISTS; i++) {
    const id = uuid();
    const name = faker.music.songName();
    const userId = faker.helpers.arrayElement(userIds);
    const visibility = "PUBLIC"
    const isSystem = false;

    playlistPromises.push(
      (async () => {
        const coverUrl = faker.image.url();
        const s3Url = await uploadToS3(coverUrl, "playlist", "cover_url", id);
        const now = new Date();
        return [id, name, s3Url, userId, visibility, isSystem, null, now, now];
      })()
    );
  }

  const playlistRows = await Promise.all(playlistPromises);

  await db.query(
    `INSERT INTO playlist (id, name, cover_url, user_id, visibility, is_system, system_type, created_at, updated_at)
     VALUES ?`,
    [playlistRows]
  );

  for (const playlist of playlistRows) {
    const playlistId = playlist[0];
    const songs = faker.helpers.arrayElements(
      songIds,
      faker.number.int({ min: 5, max: 25 })
    );
    for (let i = 0; i < songs.length; i++) {
      await db.query(
        `INSERT INTO playlist_songs (playlist_id, song_id, song_order)
         VALUES (?, ?, ?)`,
        [playlistId, songs[i], i]
      );
    }
  }

  return playlistRows.map(row => row[0]);
}

async function seedLikedSongsPlaylists(userIds) {
  const playlists = userIds.map(userId => {
    const now = new Date();
    return [
      `LIKED_SONGS_${userId}`,
      "Liked Songs",
      null,
      userId,
      "PRIVATE",
      true,
      "LIKED_SONGS",
      now,
      now
    ];
  });

  await db.query(
    `INSERT INTO playlist (id, name, cover_url, user_id, visibility, is_system, system_type, created_at, updated_at)
     VALUES ?`,
    [playlists]
  );
}

async function seedLikedPlaylists(userIds, playlistIds) {
  for (const userId of userIds) {
    const liked = faker.helpers.arrayElements(
      playlistIds,
      faker.number.int({ min: 1, max: Math.min(10, playlistIds.length) })
    );
    for (let i = 0; i < liked.length; i++) {
      const playlistId = liked[i];
      await db.query(
        `INSERT INTO liked_playlists (user_id, playlist_id, like_order)
         VALUES (?, ?, ?)`,
        [userId, playlistId, i]
      );
    }
  }
}

/* ===============================
   RUN
   =============================== */

(async () => {
  try {
    console.log("🚀 Seeding database...");
    await truncateAll();

    const userIds = await seedUsers();
    const genreIds = await seedGenres();
    const artistIds = await seedArtists();
    const recordIds = await seedRecords(artistIds);
    const songIds = await seedSongs(recordIds, artistIds, genreIds);

    const playlistIds = await seedPlaylists(userIds, songIds);
    await seedLikedSongsPlaylists(userIds);
    await seedLikedPlaylists(userIds, playlistIds);
    await seedFollowers(userIds, artistIds);
    await seedPlayHistory(userIds, songIds);

    console.log("✅ Database seeded successfully");
  } catch (err) {
    console.error("❌ Seeding failed", err);
  } finally {
    await db.end();
  }
})();
