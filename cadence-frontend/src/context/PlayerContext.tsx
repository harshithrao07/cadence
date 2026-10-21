"use client";

import React, { createContext, useContext, useState, useRef, useEffect } from "react";
import { EachSongDTO } from "@/types/Song";
import { PlayerContextType, SongStreamUrlDTO } from "@/types/Player";
import { ApiResponseDTO } from "@/types/ApiResponse";
import api from "@/lib/api";

const PlayerContext = createContext<PlayerContextType | undefined>(undefined);

// A preloaded URL with less time left than this could expire mid-song, so it is fetched again instead
const PRELOAD_MIN_REMAINING_MS = 10 * 60 * 1000;

interface PreloadedSong {
  songId: string;
  url: string;
  expiresAt: number | null;
  audio: HTMLAudioElement;
}

// Stops an element downloading; removing src alone doesn't abort the request until load() runs
const releaseAudio = (audio: HTMLAudioElement) => {
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
};

export const usePlayer = () => {
  const context = useContext(PlayerContext);
  if (!context) {
    throw new Error("usePlayer must be used within a PlayerProvider");
  }
  return context;
};

export const PlayerProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentSong, setCurrentSong] = useState<EachSongDTO | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.5);
  const [queue, setQueue] = useState<EachSongDTO[]>([]);
  const [queueIndex, setQueueIndex] = useState<number>(-1);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const unbindAudioRef = useRef<(() => void) | null>(null);
  const onEndedRef = useRef<() => void>(() => {});
  // The next song in the queue, already buffering in its own element so it starts without a wait
  const preloadRef = useRef<PreloadedSong | null>(null);
  const activeUrlRef = useRef<string | null>(null);
  const queueRef = useRef<EachSongDTO[]>([]);
  const queueIndexRef = useRef<number>(-1);
  const currentSongRef = useRef<EachSongDTO | null>(null);
  const playRequestIdRef = useRef(0);
  const isSeekingRef = useRef(false);
  const targetSeekTimeRef = useRef(0);

  // Wires player state to the element that is playing; it changes when a preloaded song takes over
  const bindAudio = React.useCallback((audio: HTMLAudioElement) => {
    const handleTimeUpdate = () => {
      if (isSeekingRef.current) {
          // If we are seeking, only update if we are close to the target
          // This prevents "jumping back" to old time before seek takes effect
          if (Math.abs(audio.currentTime - targetSeekTimeRef.current) < 0.5) {
              isSeekingRef.current = false;
          } else {
              return;
          }
      }
      setCurrentTime(audio.currentTime);
    };
    const handleDurationChange = () => {
      const d = audio.duration;
      if (Number.isFinite(d)) {
        setDuration(d);
      } else if (currentSongRef.current?.totalDuration) {
        setDuration(currentSongRef.current.totalDuration);
      }
    };
    const handleWaiting = () => setIsBuffering(true);
    const handlePlaying = () => setIsBuffering(false);
    const handleCanPlay = () => setIsBuffering(false);
    const handleEnded = () => onEndedRef.current();

    audio.addEventListener("timeupdate", handleTimeUpdate);
    audio.addEventListener("durationchange", handleDurationChange);
    audio.addEventListener("waiting", handleWaiting);
    audio.addEventListener("playing", handlePlaying);
    audio.addEventListener("canplay", handleCanPlay);
    audio.addEventListener("ended", handleEnded);

    return () => {
      audio.removeEventListener("timeupdate", handleTimeUpdate);
      audio.removeEventListener("durationchange", handleDurationChange);
      audio.removeEventListener("waiting", handleWaiting);
      audio.removeEventListener("playing", handlePlaying);
      audio.removeEventListener("canplay", handleCanPlay);
      audio.removeEventListener("ended", handleEnded);
    };
  }, []);

  const activateAudio = React.useCallback((audio: HTMLAudioElement) => {
    if (audioRef.current && audioRef.current !== audio) {
      audio.volume = audioRef.current.volume;
    }
    unbindAudioRef.current?.();
    audioRef.current = audio;
    unbindAudioRef.current = bindAudio(audio);
  }, [bindAudio]);

  const discardPreload = React.useCallback(() => {
    if (preloadRef.current) {
      releaseAudio(preloadRef.current.audio);
      preloadRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const audio = new Audio();
      audio.volume = volume;
      activateAudio(audio);

      return () => {
        unbindAudioRef.current?.();
        unbindAudioRef.current = null;
        if (audioRef.current) {
          audioRef.current.pause();
          audioRef.current.src = "";
        }
        discardPreload();
        if (activeUrlRef.current) {
          URL.revokeObjectURL(activeUrlRef.current);
          activeUrlRef.current = null;
        }
      };
    }
    // Both callbacks are stable; volume is only the initial value, later changes go through the volume effect
  }, [activateAudio, discardPreload]);

  useEffect(() => {
    queueRef.current = queue;
  }, [queue]);

  useEffect(() => {
    currentSongRef.current = currentSong;
  }, [currentSong]);

  useEffect(() => {
    queueIndexRef.current = queueIndex;
  }, [queueIndex]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume;
    }
  }, [volume]);

  // Preload the next song in the queue
  useEffect(() => {
    const next = queue[queueIndex + 1];
    if (preloadRef.current && preloadRef.current.songId === next?.id) return;
    discardPreload();
    if (!next || (next as any).file) return;

    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const res = await api.get<ApiResponseDTO<SongStreamUrlDTO>>(`api/v1/stream/song/${next.id}`, { silentErrors: true });
        if (cancelled) return;
        const { url, expiresAt } = res.data.data;
        const audio = new Audio();
        audio.preload = "auto";
        audio.src = url;
        preloadRef.current = { songId: next.id, url, expiresAt: expiresAt ? Date.parse(expiresAt) : null, audio };
      } catch (e) {
        // Not fatal: the song loads normally when it comes up
      }
    }, 500);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [queue, queueIndex, discardPreload]);

  const loadAndPlaySong = React.useCallback(
    async (song: EachSongDTO, startTime: number = 0) => {
      if (!audioRef.current) return;

      const requestId = ++playRequestIdRef.current;

      let audio = audioRef.current;
      audio.pause();
      audio.currentTime = startTime;

      setCurrentSong(song);
      setCurrentTime(startTime);
      setDuration(song.totalDuration || 0);

      try {
        const localFile = (song as any).file as File | undefined;

        let preloaded = preloadRef.current?.songId === song.id ? preloadRef.current : null;
        if (preloaded && preloaded.expiresAt !== null && preloaded.expiresAt - Date.now() < PRELOAD_MIN_REMAINING_MS) {
          // Its URL could expire before the song finishes; load it fresh instead
          discardPreload();
          preloaded = null;
        }

        if (preloaded) {
          // Already buffering: swap its element in instead of loading the song again
          preloadRef.current = null;
          releaseAudio(audio);
          if (activeUrlRef.current && activeUrlRef.current.startsWith("blob:")) {
            window.URL.revokeObjectURL(activeUrlRef.current);
          }
          activateAudio(preloaded.audio);
          audio = preloaded.audio;
          activeUrlRef.current = preloaded.url;
          if (Number.isFinite(audio.duration)) {
            setDuration(audio.duration);
          }
        } else {
          let url: string;
          if (localFile) {
            const blob = new Blob([localFile], { type: localFile.type || "audio/mpeg" });
            url = window.URL.createObjectURL(blob);
          } else {
            // A short-lived URL; the browser streams and seeks it natively
            const res = await api.get<ApiResponseDTO<SongStreamUrlDTO>>(`api/v1/stream/song/${song.id}`);
            url = res.data.data.url;
          }

          if (requestId !== playRequestIdRef.current) {
            if (url.startsWith("blob:")) {
              window.URL.revokeObjectURL(url);
            }
            return;
          }

          if (activeUrlRef.current && activeUrlRef.current.startsWith("blob:")) {
            window.URL.revokeObjectURL(activeUrlRef.current);
          }
          activeUrlRef.current = url;
          audio.src = url;
        }

        if (startTime > 0) {
          audio.currentTime = startTime;
        }

        await audio.play();

        if (requestId !== playRequestIdRef.current) {
          audio.pause();
          return;
        }

        setIsPlaying(true);

        // Count the play once it has actually started; resuming mid-song isn't a new play
        if (!localFile && startTime === 0) {
          api.post(`api/v1/stream/song/${song.id}/plays`, null, { silentErrors: true }).catch(() => {});
        }
      } catch (error: any) {
        if (error && (error.code === "ERR_CANCELED" || error.name === "CanceledError" || error.name === "AbortError")) {
          return;
        }
        console.error("Error playing song:", error);
        setIsPlaying(false);
      }
    },
    [activateAudio, discardPreload]
  );

  useEffect(() => {
    onEndedRef.current = () => {
      const q = queueRef.current;
      const idx = queueIndexRef.current;

      if (q.length > 0 && idx >= 0 && idx < q.length - 1) {
        const nextIndex = idx + 1;
        setQueueIndex(nextIndex);
        loadAndPlaySong(q[nextIndex]);
      } else {
        setIsPlaying(false);
        setCurrentTime(0);
      }
    };
  }, [loadAndPlaySong]);

  const playSong = React.useCallback(
    (song: EachSongDTO) => {
      setQueue([song]);
      setQueueIndex(0);
      queueRef.current = [song];
      queueIndexRef.current = 0;
      loadAndPlaySong(song);
    },
    [loadAndPlaySong]
  );

  const playQueue = React.useCallback(
    (songs: EachSongDTO[], startIndex: number = 0) => {
      if (!songs.length || startIndex < 0 || startIndex >= songs.length) return;
      setQueue(songs);
      setQueueIndex(startIndex);
      queueRef.current = songs;
      queueIndexRef.current = startIndex;
      loadAndPlaySong(songs[startIndex]);
    },
    [loadAndPlaySong]
  );

  const playNext = React.useCallback(() => {
    const q = queueRef.current;
    const idx = queueIndexRef.current;
    if (q.length === 0 || idx < 0 || idx >= q.length - 1) return;
    const nextIndex = idx + 1;
    setQueueIndex(nextIndex);
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
    }
    loadAndPlaySong(q[nextIndex]);
  }, [loadAndPlaySong]);

  const playPrevious = React.useCallback(() => {
    const q = queueRef.current;
    const idx = queueIndexRef.current;
    if (q.length === 0 || idx <= 0) return;
    const prevIndex = idx - 1;
    setQueueIndex(prevIndex);
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
    }
    loadAndPlaySong(q[prevIndex]);
  }, [loadAndPlaySong]);

  const pauseSong = React.useCallback(() => {
    // We don't increment playRequestIdRef here because we might want to resume the same song session.
    if (audioRef.current) {
      audioRef.current.pause();
    }
    setIsPlaying(false);
  }, []);

  const resumeSong = React.useCallback(() => {
    if (audioRef.current && currentSong) {
      if ((!audioRef.current.src || audioRef.current.src === window.location.href) && !activeUrlRef.current) {
         loadAndPlaySong(currentSong, currentTime);
         return;
      }

      audioRef.current.play();
      setIsPlaying(true);
    }
  }, [currentSong, currentTime, loadAndPlaySong]);

  const togglePlay = React.useCallback(() => {
    if (isPlaying) {
      pauseSong();
    } else {
      resumeSong();
    }
  }, [isPlaying, pauseSong, resumeSong]);

  const seekTo = (time: number) => {
    if (audioRef.current) {
      // Set seeking state to prevent UI jumps
      isSeekingRef.current = true;
      targetSeekTimeRef.current = time;
      
      // Immediate UI feedback
      setCurrentTime(time);
      setIsBuffering(true);

      audioRef.current.currentTime = time;
    }
  };

  const resetPlayer = React.useCallback(() => {
    playRequestIdRef.current++;
    discardPreload();
    const audio = audioRef.current;
    if (audio) {
      audio.pause();
      audio.currentTime = 0;
      audio.src = "";
    }
    if (activeUrlRef.current && activeUrlRef.current.startsWith("blob:")) {
      URL.revokeObjectURL(activeUrlRef.current);
      activeUrlRef.current = null;
    }
    setCurrentSong(null);
    setIsPlaying(false);
    setCurrentTime(0);
    setDuration(0);
    setQueue([]);
    setQueueIndex(-1);
    queueRef.current = [];
    queueIndexRef.current = -1;
  }, [discardPreload]);

  const removeFromQueue = React.useCallback((index: number) => {
    setQueue((prevQueue) => {
      const newQueue = [...prevQueue];
      newQueue.splice(index, 1);
      
      if (index < queueIndexRef.current) {
        setQueueIndex(queueIndexRef.current - 1);
        queueIndexRef.current = queueIndexRef.current - 1;
      }
      
      queueRef.current = newQueue;
      return newQueue;
    });
  }, []);

  const addToQueue = React.useCallback((song: EachSongDTO) => {
    setQueue((prevQueue) => {
      const currentIndex = queueIndexRef.current;
      const newQueue = [...prevQueue];
      
      if (currentIndex === -1 || newQueue.length === 0) {
        newQueue.push(song);
      } else {
        newQueue.splice(currentIndex + 1, 0, song);
      }
      
      queueRef.current = newQueue;
      return newQueue;
    });
  }, []);

  return (
    <PlayerContext.Provider
      value={{
        currentSong,
        isPlaying,
        isBuffering,
        currentTime,
        duration,
        volume,
        queue,
        queueIndex,
        playSong,
        pauseSong,
        resumeSong,
        seekTo,
        setVolume,
        togglePlay,
        playQueue,
        playNext,
        playPrevious,
        resetPlayer,
        removeFromQueue,
        addToQueue,
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
};
