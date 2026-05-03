import { ArtistPreviewDTO } from "./Artists";
import { PlaylistPreviewDTO } from "./Playlist";
import { RecordPreviewDTO } from "./Record";
import { EachSongDTO } from "./Song";

export interface GlobalSearchDTO {
  artists: ArtistPreviewDTO[];
  records: RecordPreviewDTO[];
  songs: EachSongDTO[];
  playlists: PlaylistPreviewDTO[];  
}

export interface DiscoverDTO {
  trendingSongs: EachSongDTO[];
  popularArtists: ArtistPreviewDTO[];
  recommendedSongs: EachSongDTO[];
  newReleases: RecordPreviewDTO[];
  newReleasesOfFollowingArtists: RecordPreviewDTO[];
  recentlyPlayedSongs: EachSongDTO[];
  suggestedArtists: ArtistPreviewDTO[];
}