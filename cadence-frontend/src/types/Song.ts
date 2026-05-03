import { ArtistPreviewDTO } from "./Artists";
import { GenrePreviewDTO } from "./Genre";
import { RecordPreviewWithCoverImageDTO } from "./Record";

export interface EachSongDTO {
  id: string;
  title: string;
  totalDuration: number;
  artists: ArtistPreviewDTO[];
  genres: GenrePreviewDTO[];
  recordPreviewWithCoverImageDTO: RecordPreviewWithCoverImageDTO;
}

export interface UpsertSongDTO {
  id?: string;
  title: string;
  genreIds: string[];
  artistIds: string[];
}

export interface TopSongsInArtistProfileDTO {
  id: string;
  title: string;
  totalDuration: number;
  coverUrl: string;
  totalPlays: number;
  recordId: string;
  recordTitle: string;
  artists: ArtistPreviewDTO[];
}

export interface SongResponseDTO {
  id: string;
  title: string;
  presignedUrl: string;
}