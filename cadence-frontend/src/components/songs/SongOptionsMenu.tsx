"use client";

import { useState, useRef, useEffect } from "react";
import { createPortal } from "react-dom";
import { MoreVertical, Plus, ListMusic, X, Music, Heart, ListPlus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/api";
import { PlaylistPreviewDTO, UpsertPlaylistDTO } from "@/types/Playlist";
import { useUser } from "@/context/UserContext";
import { usePlayer } from "@/context/PlayerContext";
import { EachSongDTO } from "@/types/Song";

interface SongOptionsMenuProps {
  songId: string;
  song?: EachSongDTO;
}

export default function SongOptionsMenu({ songId, song }: SongOptionsMenuProps) {
  const { likedSongIds, toggleLike } = useUser();
  const { addToQueue, queue, removeFromQueue } = usePlayer();
  const [isOpen, setIsOpen] = useState(false);
  const [showPlaylistModal, setShowPlaylistModal] = useState(false);
  const [playlists, setPlaylists] = useState<PlaylistPreviewDTO[]>([]);
  const [loadingPlaylists, setLoadingPlaylists] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [mounted, setMounted] = useState(false);
  
  const menuRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);

      // Close menu when mouse leaves the song row (parent .group element)
      const menuElement = menuRef.current;
      const groupParent = menuElement?.closest('.group');
      
      const handleMouseLeave = () => {
        setIsOpen(false);
      };

      if (groupParent) {
        groupParent.addEventListener('mouseleave', handleMouseLeave);
      }

      return () => {
        document.removeEventListener("mousedown", handleClickOutside);
        if (groupParent) {
          groupParent.removeEventListener('mouseleave', handleMouseLeave);
        }
      };
    }
  }, [isOpen]);

  const handleOpenPlaylists = async () => {
    setIsOpen(false);
    setShowPlaylistModal(true);
    setLoadingPlaylists(true);
    try {
      const res = await api.get("/api/v1/playlist/all");
      setPlaylists(res.data.data || res.data || []);
    } catch (error) {
      console.error("Failed to fetch playlists", error);
      toast.error("Failed to load playlists");
    } finally {
      setLoadingPlaylists(false);
    }
  };

  const handleAddToPlaylist = async (playlistId: string) => {
    try {
      await api.put(`/api/v1/playlist/${playlistId}/song/${songId}`);
      toast.success("Song added to playlist");
      setShowPlaylistModal(false);
    } catch (error) {
      console.error("Failed to add song to playlist", error);
      toast.error("Failed to add song to playlist");
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      const url = URL.createObjectURL(file);
      setPreviewUrl(url);
    }
  };

  const handleCreatePlaylist = async () => {
    if (!newPlaylistName.trim()) return;

    try {
      setIsCreating(true);
      const payload: UpsertPlaylistDTO = {
        name: newPlaylistName.trim(),
        // visibility: "PUBLIC" // Optional, defaulting to backend default
      };
      
      const res = await api.post("/api/v1/playlist/upsert", payload);
      const responseData = res.data.data;
      const playlistId = typeof responseData === "object" ? responseData.id : responseData;
      
      if (playlistId) {
        // Upload cover image if selected
        if (selectedFile) {
          try {
            const formData = new FormData();
            formData.append("file", selectedFile, `playlist cover_url ${playlistId}`);
            await api.post("/api/v1/files", formData, {
              headers: { "Content-Type": "multipart/form-data" },
            });
          } catch (uploadError) {
            console.error("Failed to upload playlist cover", uploadError);
            toast.error("Playlist created but failed to upload cover");
          }
        }

        // Add song to the new playlist
        await handleAddToPlaylist(playlistId);
      } else {
        throw new Error("Invalid playlist ID in response");
      }
      
      setNewPlaylistName("");
      setSelectedFile(null);
      setPreviewUrl(null);
      setIsCreating(false);
    } catch (error) {
      console.error("Failed to create playlist", error);
      toast.error("Failed to create playlist");
      setIsCreating(false);
    }
  };

  return (
    <div className="relative" ref={menuRef} onClick={(e) => e.stopPropagation()}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="p-2 text-gray-400 hover:text-white rounded-full hover:bg-white/10 transition"
      >
        <MoreVertical className="w-4 h-4" />
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full pt-2 w-48 z-50">
          <div className="bg-black border border-zinc-800 rounded-md shadow-lg py-1">
          <button
            onClick={(e) => {
              toggleLike(songId, e);
              setIsOpen(false);
            }}
            className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-zinc-800 hover:text-white flex items-center gap-2"
          >
            <Heart className={`w-4 h-4 ${likedSongIds.has(songId) ? "fill-red-500 text-red-500" : ""}`} />
            {likedSongIds.has(songId) ? "Remove from Liked" : "Save to Liked Songs"}
          </button>
          <button
            onClick={handleOpenPlaylists}
            className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-zinc-800 hover:text-white flex items-center gap-2"
          >
            <ListMusic className="w-4 h-4" />
            Add to Playlist
          </button>
          {song && (
            <>
              <button
                onClick={() => {
                  addToQueue(song);
                  setIsOpen(false);
                  toast.success("Added to queue");
                }}
                className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-zinc-800 hover:text-white flex items-center gap-2"
              >
                <ListPlus className="w-4 h-4" />
                Add to Queue
              </button>
              {queue.some(s => s.id === song.id) && (
                <button
                  onClick={() => {
                    // Find all indices of this song in the queue
                    const indices = queue
                      .map((s, i) => s.id === song.id ? i : -1)
                      .filter(i => i !== -1)
                      .sort((a, b) => b - a); // Sort descending to remove safely
                    
                    indices.forEach(index => removeFromQueue(index));
                    
                    setIsOpen(false);
                    toast.success("Removed from queue");
                  }}
                  className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-zinc-800 hover:text-white flex items-center gap-2"
                >
                  <Trash2 className="w-4 h-4" />
                  Remove from Queue
                </button>
              )}
            </>
          )}
          </div>
        </div>
      )}

      {mounted && showPlaylistModal && createPortal(
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4" onClick={() => setShowPlaylistModal(false)}>
          <div className="bg-black border border-zinc-800 rounded-xl w-full max-w-md shadow-2xl flex flex-col max-h-[80vh]" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h3 className="text-lg font-bold">Add to Playlist</h3>
              <button 
                onClick={() => setShowPlaylistModal(false)}
                className="text-zinc-400 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4 border-b border-zinc-800">
              <div className="flex gap-3">
                <div 
                  onClick={() => fileInputRef.current?.click()}
                  className={`w-10 h-10 flex-shrink-0 bg-zinc-900 rounded-md flex items-center justify-center cursor-pointer overflow-hidden ${!previewUrl ? "border border-zinc-700" : ""}`}
                >
                  {previewUrl ? (
                    <img src={previewUrl} alt="Cover" className="w-full h-full object-cover" />
                  ) : (
                    <Music className="w-5 h-5 text-zinc-500" />
                  )}
                </div>
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  className="hidden" 
                  accept="image/*"
                  onChange={handleFileSelect}
                />
                <input
                  type="text"
                  placeholder="New Playlist Name"
                  value={newPlaylistName}
                  onChange={(e) => setNewPlaylistName(e.target.value)}
                  className="flex-1 bg-white border-none rounded-md px-3 py-2 text-sm text-black placeholder-zinc-500 focus:ring-1 focus:ring-red-500 outline-none h-10"
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleCreatePlaylist();
                  }}
                />
                <button
                  onClick={handleCreatePlaylist}
                  disabled={!newPlaylistName.trim() || isCreating}
                  className="bg-red-500 hover:bg-red-600 disabled:bg-red-500/50 text-white px-3 rounded-md text-sm font-medium transition flex items-center gap-1 h-10"
                >
                  {isCreating ? (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  ) : (
                    <Plus className="w-4 h-4" />
                  )}
                  Create
                </button>
              </div>
            </div>

            <div className="overflow-y-auto flex-1 p-2 custom-scrollbar">
              {loadingPlaylists ? (
                <div className="flex justify-center py-8">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-red-500"></div>
                </div>
              ) : playlists.length > 0 ? (
                <div className="space-y-1">
                  {playlists.map((playlist) => (
                    <button
                      key={playlist.id}
                      onClick={() => handleAddToPlaylist(playlist.id)}
                      className="w-full text-left px-4 py-3 rounded-lg hover:bg-zinc-800 transition flex items-center justify-between group"
                    >
                      <span className="font-medium text-gray-200 group-hover:text-white truncate">{playlist.name}</span>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="text-center py-8 text-zinc-500">
                  No playlists found. Create one above!
                </div>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
