"use client";

import React, { createContext, useContext, useState, useRef, useEffect } from "react";
import { EachSongDTO } from "@/types/Song";
import { PlayerContextType } from "@/types/Player";
import api from "@/lib/api";

const PlayerContext = createContext<PlayerContextType | undefined>(undefined);

export const usePlayer = () => {
  const context = useContext(PlayerContext);
  if (!context) {
    throw new Error("usePlayer must be used within a PlayerProvider");
  }
  return context;
};

// Helper function for streaming with auth handling
const fetchStreamWithAuth = async (url: string, signal: AbortSignal, headers: Record<string, string> = {}) => {
  const getAuthHeader = () => {
    if (typeof window === "undefined") return {};
    try {
      const authDetails = localStorage.getItem("auth_details");
      if (!authDetails) return {};
      const { accessToken } = JSON.parse(authDetails);
      return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
    } catch (e) {
      return {};
    }
  };

  const baseUrl = process.env.NEXT_PUBLIC_API_URL || "";
  // Ensure no double slash if url starts with /
  const fullUrl = url.startsWith("http") ? url : `${baseUrl.replace(/\/$/, "")}/${url.replace(/^\//, "")}`;

  let response = await fetch(fullUrl, {
    headers: { ...getAuthHeader(), ...headers },
    signal,
  });

  if (response.status === 401) {
    // Try refresh
    try {
      const refreshRes = await api.post('auth/v1/refresh', {}, { withCredentials: true });
      const accessToken = refreshRes.data;
      
      // Update local storage
      const oldAuthStr = localStorage.getItem("auth_details");
      const oldAuth = oldAuthStr ? JSON.parse(oldAuthStr) : {};
      localStorage.setItem("auth_details", JSON.stringify({ ...oldAuth, accessToken }));

      // Retry
      response = await fetch(fullUrl, {
        headers: { Authorization: `Bearer ${accessToken}`, ...headers },
        signal,
      });
    } catch (e) {
      throw new Error("Session expired");
    }
  }

  if (!response.ok) {
    throw new Error(`Stream request failed: ${response.status}`);
  }

  return response;
};

const PRELOAD_BYTES = 512 * 1024; // 512KB

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
  const activeUrlRef = useRef<string | null>(null);
  const queueRef = useRef<EachSongDTO[]>([]);
  const queueIndexRef = useRef<number>(-1);
  const currentSongRef = useRef<EachSongDTO | null>(null);
  const requestControllerRef = useRef<AbortController | null>(null);
  const playRequestIdRef = useRef(0);
  const preloadCache = useRef<Map<string, { buffer: ArrayBuffer, contentType: string }>>(new Map());
  const onSeekRef = useRef<(() => void) | null>(null);
  const onResumeFetchRef = useRef<(() => void) | null>(null);
  const isSeekingRef = useRef(false);
  const targetSeekTimeRef = useRef(0);

  useEffect(() => {
    if (typeof window !== "undefined") {
      audioRef.current = new Audio();
      audioRef.current.volume = volume;

      const audio = audioRef.current;

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
      const handleCanPlay = () => {
          setIsBuffering(false);
          // If we were supposed to be playing but were paused for buffering, resume now
          if (isPlaying && audio.paused) {
              audio.play();
          }
      };
      const handleSeeking = () => {
        if (onSeekRef.current) onSeekRef.current();
      };
      
      audio.addEventListener("timeupdate", handleTimeUpdate);
      audio.addEventListener("durationchange", handleDurationChange);
      audio.addEventListener("waiting", handleWaiting);
      audio.addEventListener("playing", handlePlaying);
      audio.addEventListener("canplay", handleCanPlay);
      audio.addEventListener("seeking", handleSeeking);

      return () => {
        audio.removeEventListener("timeupdate", handleTimeUpdate);
        audio.removeEventListener("durationchange", handleDurationChange);
        audio.removeEventListener("waiting", handleWaiting);
        audio.removeEventListener("playing", handlePlaying);
        audio.removeEventListener("canplay", handleCanPlay);
        audio.removeEventListener("seeking", handleSeeking);

        audio.pause();
        audio.src = "";
        if (activeUrlRef.current) {
          URL.revokeObjectURL(activeUrlRef.current);
          activeUrlRef.current = null;
        }
      };
    }
  }, []);

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
    const preloadNext = async () => {
      const q = queueRef.current;
      const idx = queueIndexRef.current;
      // If queue is empty or index invalid, don't preload, unless index is -1 but queue has items (start)
      if (q.length === 0) return;

      // Preload next 2 songs
      for (let i = 1; i <= 2; i++) {
        const nextIdx = idx + i;
        if (nextIdx >= q.length) break;

        const nextSong = q[nextIdx];
        if (!nextSong || preloadCache.current.has(nextSong.id)) continue;

        try {
          if ((nextSong as any).file) continue;

          const endpoint = `api/v1/song/stream/${nextSong.id}`;
          const controller = new AbortController();

          const response = await fetchStreamWithAuth(endpoint, controller.signal, { Range: `bytes=0-${PRELOAD_BYTES}` });
          if (response.status === 206 || response.status === 200) {
            const contentType = response.headers.get('Content-Type') || 'audio/mpeg';
            const buffer = await response.arrayBuffer();
            if (buffer.byteLength > 0) {
              preloadCache.current.set(nextSong.id, { buffer, contentType });
            }
          }
        } catch (e) {
          // Ignore preload errors
        }
      }

      // Cleanup cache
      const validIds = new Set<string>();
      // Keep current song in cache if needed? No, once playing, we don't need it in preload cache usually,
      // but if we seek back or restart, maybe? But loadAndPlaySong handles it.
      // Let's keep a window around current index.
      const startKeep = Math.max(0, idx);
      for (let i = startKeep; i < Math.min(idx + 5, q.length); i++) {
        validIds.add(q[i].id);
      }
      for (const id of preloadCache.current.keys()) {
        if (!validIds.has(id)) {
          preloadCache.current.delete(id);
        }
      }
    };

    const timer = setTimeout(preloadNext, 500);
    return () => clearTimeout(timer);
  }, [queue, queueIndex]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume;
    }
  }, [volume]);

  const loadAndPlaySong = React.useCallback(
    async (song: EachSongDTO, startTime: number = 0) => {
      if (!audioRef.current) return;

      const requestId = ++playRequestIdRef.current;

      if (requestControllerRef.current) {
        requestControllerRef.current.abort();
        requestControllerRef.current = null;
      }

      const audio = audioRef.current;
      audio.pause();
      audio.currentTime = startTime;

      setCurrentSong(song);
      setCurrentTime(startTime);
      setDuration(song.totalDuration || 0);

      try {
        let url: string | null = null;
        const localFile = (song as any).file as File | undefined;
        let controller: AbortController | null = null;

        if (localFile) {
          const blob = new Blob([localFile], { type: localFile.type || "audio/mpeg" });
          url = window.URL.createObjectURL(blob);
        } else {
          // Stream using MediaSource and fetchWithAuth
          const endpoint = `api/v1/song/stream/${song.id}`;

          controller = new AbortController();
          requestControllerRef.current = controller;

          try {
            const mediaSource = new MediaSource();
            url = window.URL.createObjectURL(mediaSource);

            let sourceBuffer: SourceBuffer | null = null;
            let totalSizeBytes = 0;
            let seekTimeout: NodeJS.Timeout | null = null;

            const fetchAndAppend = async (startByte: number, timeOffset: number = 0) => {
                if (controller) controller.abort();
                controller = new AbortController();
                requestControllerRef.current = controller;

                try {
                    const response = await fetchStreamWithAuth(endpoint, controller.signal, { Range: `bytes=${startByte}-` });
                    
                    if (response.status === 206 || response.status === 200) {
                        const range = response.headers.get('Content-Range');
                        if (range) {
                            const parts = range.split('/');
                            if (parts[1] && parts[1] !== '*') totalSizeBytes = parseInt(parts[1], 10);
                        } else if (response.status === 200) {
                            const len = response.headers.get('Content-Length');
                            if (len) totalSizeBytes = parseInt(len, 10);
                        }
                    }

                    if (!sourceBuffer || mediaSource.readyState !== 'open') return;

                    if (timeOffset > 0 && !sourceBuffer.updating) {
                        try {
                             // Reset source buffer parsing state for new segment
                             sourceBuffer.abort(); 
                             sourceBuffer.timestampOffset = timeOffset;
                        } catch (e) { console.error(e); }
                    }

                    if (!response.body) return;
                    const reader = response.body.getReader();

                    const pump = async () => {
                        try {
                            while (true) {
                                const { done, value } = await reader.read();
                                if (done) break;
                                
                                if (sourceBuffer.updating) {
                                    await new Promise(r => sourceBuffer.addEventListener('updateend', r, { once: true }));
                                }
                                if (mediaSource.readyState === 'open') {
                                    sourceBuffer.appendBuffer(value);
                                }
                            }
                        } catch (e) {
                            // ignore
                        }
                    };
                    await pump();
                } catch (e: any) {
                    if (e.code === "ERR_CANCELED" || e.name === "CanceledError" || e.name === "AbortError") {
                        return;
                    }
                    console.error("Stream fetch error:", e);
                    setIsBuffering(false);
                }
            };

            onSeekRef.current = () => {
                if (!sourceBuffer || totalSizeBytes === 0) return;
                const seekTime = audio.currentTime;
                
                // Check if buffered
                let isBuffered = false;
                for (let i = 0; i < sourceBuffer.buffered.length; i++) {
                    if (seekTime >= sourceBuffer.buffered.start(i) && seekTime <= sourceBuffer.buffered.end(i) - 0.5) {
                        isBuffered = true;
                        break;
                    }
                }

                if (isBuffered) {
                    return;
                }
                
                // Stop playback if unbuffered
                audio.pause();
                setIsBuffering(true);
                
                if (seekTimeout) clearTimeout(seekTimeout);
                seekTimeout = setTimeout(() => {
                    const duration = audio.duration || song.totalDuration || 1;
                    
                    // Find gap start (end of the buffered range immediately preceding seekTime)
                    let startByteTime = 0;
                    for (let i = 0; i < sourceBuffer.buffered.length; i++) {
                        const end = sourceBuffer.buffered.end(i);
                        if (end < seekTime) {
                            startByteTime = end;
                        } else {
                            break;
                        }
                    }

                    // Estimate byte position based on the gap start
                    const bytePos = Math.floor((startByteTime / duration) * totalSizeBytes);
                    fetchAndAppend(bytePos, startByteTime);
                }, 200);
            };

            onResumeFetchRef.current = () => {
                if (!sourceBuffer || mediaSource.readyState !== 'open') return;
                // If we are already fetching, don't interrupt
                if (requestControllerRef.current && !requestControllerRef.current.signal.aborted) return;
                
                const seekTime = audio.currentTime;
                // Find end of buffered range covering seekTime
                let startByteTime = seekTime;
                for(let i=0; i<sourceBuffer.buffered.length; i++) {
                    if(sourceBuffer.buffered.start(i) <= seekTime + 0.1 && sourceBuffer.buffered.end(i) >= seekTime - 0.1) {
                        startByteTime = sourceBuffer.buffered.end(i);
                        break;
                    }
                }
                
                // If buffered up to near end, no need to fetch
                if (startByteTime >= (song.totalDuration || audio.duration || 1) - 1) return;
                
                const bytePos = Math.floor((startByteTime / (song.totalDuration || audio.duration || 1)) * totalSizeBytes);
                // Resume fetching from end of buffer
                // We use timestampOffset = startByteTime because we are appending from there
                fetchAndAppend(bytePos, startByteTime);
            };

            mediaSource.addEventListener('sourceopen', async () => {
                if (mediaSource.readyState !== 'open') return;

                const cached = preloadCache.current.get(song.id);
                let contentType = 'audio/mpeg';
                let initialBuffer: ArrayBuffer | null = null;

                if (cached) {
                    initialBuffer = cached.buffer;
                    contentType = cached.contentType;
                } else {
                     // If not cached, we start fetching from 0. 
                     // We need content type. We can fetch first byte or just start stream.
                     // To be safe, let's start stream 0- and peek headers.
                }

                const mime = contentType;
                const codec = MediaSource.isTypeSupported(mime) ? mime : 'audio/mpeg';

                try {
                    // Set duration if known to prevent playback issues
                    if (song.totalDuration && Number.isFinite(song.totalDuration)) {
                        mediaSource.duration = song.totalDuration;
                    }
                    
                    sourceBuffer = mediaSource.addSourceBuffer(codec);
                    
                    if (cached && initialBuffer) {
                        sourceBuffer.appendBuffer(initialBuffer);
                        // Fetch rest
                        fetchAndAppend(initialBuffer.byteLength, 0); // timestampOffset 0? 
                        // Actually if we append cached buffer, timestamps are 0-based.
                        // The rest should follow.
                        // fetchAndAppend aborts previous controller? No, we just started.
                        // But wait, fetchAndAppend does sourceBuffer.abort() if timeOffset > 0.
                        // Here timeOffset is 0 (or close to end of buffer). 
                        // If we pass 0, logic skips abort.
                    } else {
                        fetchAndAppend(0, 0);
                    }
                } catch (e) {
                    console.error("MediaSource error:", e);
                }
            }, { once: true });

          } catch (error: any) {
             if (error.code === "ERR_CANCELED" || error.name === "CanceledError" || error.name === "AbortError") {
                 return;
             }
             throw error;
          }
        }

        if (controller && requestControllerRef.current === controller) {
          requestControllerRef.current = null;
        }

        if (requestId !== playRequestIdRef.current) {
          if (url && typeof url === "string" && url.startsWith("blob:")) {
            window.URL.revokeObjectURL(url);
          }
          return;
        }

        if (url) {
          if (activeUrlRef.current && activeUrlRef.current.startsWith("blob:")) {
            window.URL.revokeObjectURL(activeUrlRef.current);
          }
          activeUrlRef.current = url;
          audio.src = url;
          if (startTime > 0) {
            audio.currentTime = startTime;
          }
        }

        await audio.play();

        if (requestId !== playRequestIdRef.current) {
          audio.pause();
          return;
        }

        setIsPlaying(true);
      } catch (error: any) {
        if (error && (error.code === "ERR_CANCELED" || error.name === "CanceledError" || error.name === "AbortError")) {
          return;
        }
        console.error("Error playing song:", error);
        setIsPlaying(false);
      }
    },
    []
  );

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleEnded = () => {
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

    audio.addEventListener("ended", handleEnded);
    return () => {
      audio.removeEventListener("ended", handleEnded);
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
    // We only want to stop the current network request.
    if (requestControllerRef.current) {
      requestControllerRef.current.abort();
      requestControllerRef.current = null;
    }
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
      
      // Check if we need to resume fetching
      if (onResumeFetchRef.current) {
          onResumeFetchRef.current();
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
    if (requestControllerRef.current) {
      requestControllerRef.current.abort();
      requestControllerRef.current = null;
    }
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
  }, []);

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
