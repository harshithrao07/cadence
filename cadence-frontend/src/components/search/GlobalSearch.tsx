"use client";

import React, { useEffect, useState, useRef } from "react";
import { Search, X, Music, Disc, User, ListMusic, Play } from "lucide-react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import api from "@/lib/api";
import { GlobalSearchDTO } from "@/types/Generic";
import { usePlayer } from "@/context/PlayerContext";

interface GlobalSearchProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function GlobalSearch({ isOpen, onClose }: GlobalSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GlobalSearchDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const router = useRouter();
  const { playSong } = usePlayer();

  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "unset";
    }
    return () => {
      document.body.style.overflow = "unset";
    };
  }, [isOpen]);

  useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      if (query.trim()) {
        performSearch();
      } else {
        setResults(null);
      }
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [query]);

  const performSearch = async () => {
    setLoading(true);
    try {
      const res = await api.get<any>(`/api/v1/search`, {
        params: { key: query }
      });
      const data = res.data.data || res.data;
      setResults(data);
    } catch (error) {
      console.error("Search failed", error);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setQuery("");
    setResults(null);
    onClose();
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        handleClose();
      }
    };

    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  const handleNavigation = (path: string) => {
    router.push(path);
    handleClose();
  };

  const handlePlaySong = (song: any) => {
    playSong(song);
    handleClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh] px-4">
      <div 
        className="absolute inset-0 bg-black/60 backdrop-blur-sm transition-opacity"
        onClick={handleClose}
      />

      <div className="relative w-full max-w-2xl bg-black border border-zinc-800 rounded-xl shadow-2xl overflow-hidden flex flex-col max-h-[70vh] animate-in fade-in zoom-in-95 duration-200">
        <div className="flex items-center gap-3 px-4 py-4 border-b border-zinc-800">
          <Search className="w-5 h-5 text-zinc-400" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search songs, artists, records..."
            className="flex-1 bg-transparent border-none outline-none text-lg text-white placeholder:text-zinc-500"
          />
          {query ? (
            <button onClick={() => setQuery("")} className="text-zinc-400 hover:text-white">
               <X className="w-5 h-5" />
            </button>
          ) : (
            <button onClick={handleClose} className="text-xs bg-zinc-800 px-2 py-1 rounded text-zinc-400 hover:text-white">
                ESC
            </button>
          )}
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-6 custom-scrollbar">
            {loading ? (
                <div className="py-12 flex justify-center text-zinc-500">
                    <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-red-500"></div>
                </div>
            ) : results ? (
                <>
                    {results.songs && results.songs.length > 0 && (
                        <Section title="Songs">
                            {results.songs.map(song => (
                                <div key={song.id} onClick={() => handlePlaySong(song)} className="group flex items-center gap-3 p-2 rounded-lg hover:bg-white/10 transition cursor-pointer">
                                     <div className="relative w-10 h-10 rounded overflow-hidden bg-zinc-800 flex-shrink-0">
                                        {song.recordPreviewWithCoverImageDTO?.coverUrl ? (
                                            <Image src={song.recordPreviewWithCoverImageDTO.coverUrl} alt={song.title} fill className="object-cover" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center bg-zinc-800">
                                                <Music className="w-5 h-5 text-zinc-500" />
                                            </div>
                                        )}
                                        <div className="absolute inset-0 bg-black/40 hidden group-hover:flex items-center justify-center">
                                            <Play className="w-4 h-4 text-white fill-white" />
                                        </div>
                                     </div>
                                     <div className="flex-1 min-w-0">
                                        <div className="text-sm font-medium text-white truncate group-hover:text-red-500 transition-colors">{song.title}</div>
                                        <div className="text-xs text-zinc-400 truncate">
                                            {song.artists?.map(a => a.name).join(", ") || "Unknown Artist"}
                                        </div>
                                     </div>
                                </div>
                            ))}
                        </Section>
                    )}

                     {results.records && results.records.length > 0 && (
                        <Section title="Albums & Singles">
                            {results.records.map(record => (
                                <div key={record.id} onClick={() => handleNavigation(`/records/${record.id}`)} className="group flex items-center gap-3 p-2 rounded-lg hover:bg-white/10 transition cursor-pointer">
                                     <div className="relative w-10 h-10 rounded overflow-hidden bg-zinc-800 flex-shrink-0">
                                        {record.coverUrl ? (
                                            <Image src={record.coverUrl} alt={record.title} fill className="object-cover" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center bg-zinc-800">
                                                <Disc className="w-5 h-5 text-zinc-500" />
                                            </div>
                                        )}
                                     </div>
                                     <div className="flex-1 min-w-0">
                                        <div className="text-sm font-medium text-white truncate group-hover:text-red-500 transition-colors">{record.title}</div>
                                        <div className="text-xs text-zinc-400 truncate">
                                            {record.recordType} • {record.recordArtists?.map(a => a.name).join(", ") || "Unknown Artist"}
                                        </div>
                                     </div>
                                </div>
                            ))}
                        </Section>
                    )}

                     {results.artists && results.artists.length > 0 && (
                        <Section title="Artists">
                            {results.artists.map(artist => (
                                <div key={artist.id} onClick={() => handleNavigation(`/artists/${artist.id}`)} className="group flex items-center gap-3 p-2 rounded-lg hover:bg-white/10 transition cursor-pointer">
                                     <div className="relative w-10 h-10 rounded-full overflow-hidden bg-zinc-800 flex-shrink-0">
                                        {artist.profileUrl ? (
                                            <Image src={artist.profileUrl} alt={artist.name} fill className="object-cover" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center bg-zinc-800">
                                                <User className="w-5 h-5 text-zinc-500" />
                                            </div>
                                        )}
                                     </div>
                                     <div className="flex-1 min-w-0">
                                        <div className="text-sm font-medium text-white truncate group-hover:text-red-500 transition-colors">{artist.name}</div>
                                        <div className="text-xs text-zinc-400 truncate">Artist</div>
                                     </div>
                                </div>
                            ))}
                        </Section>
                    )}

                    {results.playlists && results.playlists.length > 0 && (
                        <Section title="Playlists">
                            {results.playlists.map(playlist => (
                                <div key={playlist.id} onClick={() => handleNavigation(`/playlist/${playlist.id}`)} className="group flex items-center gap-3 p-2 rounded-lg hover:bg-white/10 transition cursor-pointer">
                                     <div className="relative w-10 h-10 rounded overflow-hidden bg-zinc-800 flex-shrink-0">
                                        {playlist.coverUrl ? (
                                            <Image src={playlist.coverUrl} alt={playlist.name} fill className="object-cover" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center bg-zinc-800">
                                                <ListMusic className="w-5 h-5 text-zinc-500" />
                                            </div>
                                        )}
                                     </div>
                                     <div className="flex-1 min-w-0">
                                        <div className="text-sm font-medium text-white truncate group-hover:text-red-500 transition-colors">{playlist.name}</div>
                                        <div className="text-xs text-zinc-400 truncate">By {playlist.owner?.name || "Unknown User"}</div>
                                     </div>
                                </div>
                            ))}
                        </Section>
                    )}
                    
                    {!results.songs?.length && !results.records?.length && !results.artists?.length && !results.playlists?.length && (
                        <div className="text-center text-zinc-500 py-8">
                            {`No results found for "${query}"`}
                        </div>
                    )}
                </>
            ) : (
                <div className="text-center text-zinc-500 py-12 flex flex-col items-center gap-2">
                    <Search className="w-8 h-8 opacity-50" />
                    <p>Start typing to search...</p>
                </div>
            )}
        </div>
      </div>
    </div>
  );
}

const Section = ({ title, children }: { title: string, children: React.ReactNode }) => (
    <div className="space-y-2">
        <h3 className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-2">{title}</h3>
        <div className="space-y-1">{children}</div>
    </div>
);