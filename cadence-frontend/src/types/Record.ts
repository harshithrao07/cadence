import { ArtistPreviewDTO } from "./Artists";
import { SongResponseDTO, UpsertSongDTO } from "./Song";

export enum RecordType {
  ALBUM = "ALBUM",
  SINGLE = "SINGLE",
  EP = "EP",
}

export const RECORD_LIMITS = {
  SINGLE: 1,
  EP: 6,    
  ALBUM: Number.MAX_SAFE_INTEGER,
};

export interface RecordPreviewDTO {
  id: string;
  title: string;
  releaseTimestamp: number;
  coverUrl: string;
  recordType: RecordType;
  recordArtists: ArtistPreviewDTO[];
}

export interface UpsertRecordDTO {
  id?: string;
  title: string;
  releaseTimestamp: number;
  recordType: RecordType;
  artistIds: string[];
  songs: UpsertSongDTO[];
}

export interface UpsertRecordResponseDTO {
  id: string;
  songs: SongResponseDTO[];
}

export interface RecordPreviewWithCoverImageDTO {
  id: string;
  title: string;
  coverUrl: string;
}