import { EachSongDTO } from "./Song";

export interface PlayerState {
  currentSong: EachSongDTO | null;
  isPlaying: boolean;
  isBuffering: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  queue: EachSongDTO[];
  queueIndex: number;
}

export interface PlayerContextType extends PlayerState {
  playSong: (song: EachSongDTO) => void;
  pauseSong: () => void;
  resumeSong: () => void;
  seekTo: (time: number) => void;
  setVolume: (volume: number) => void;
  togglePlay: () => void;
  playQueue: (songs: EachSongDTO[], startIndex?: number) => void;
  playNext: () => void;
  playPrevious: () => void;
  resetPlayer: () => void;
  removeFromQueue: (index: number) => void;
  addToQueue: (song: EachSongDTO) => void;
}
