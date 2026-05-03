import { RecordPreviewDTO } from "./Record";
import { TopSongsInArtistProfileDTO } from "./Song";

export interface UpsertArtistDTO {
  id?: string;
  name: string;
  description?: string;
}

export interface ArtistPreviewDTO {
  id: string;
  name: string;
  profileUrl: string;
}

export interface ArtistProfileDTO {
  id: string;
  name: string;
  description: string;
  profileUrl: string;
  followers: number;
  monthlyListeners: number;
  popularSongs: TopSongsInArtistProfileDTO[];
  artistRecords: RecordPreviewDTO[];
}


