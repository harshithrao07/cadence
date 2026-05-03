"use client";

import { usePlayer } from "@/context/PlayerContext";
import { useUser } from "@/context/UserContext";
import { PlaylistPreviewDTO, UpsertPlaylistDTO, PlaylistVisibility } from "@/types/Playlist";
import { EachSongDTO } from "@/types/Song";
import { Play, Pause, Clock, Heart, Shuffle, Music, Edit2, X, Camera } from "lucide-react";
import Image from "next/image";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useRef } from "react";
import { createPortal } from "react-dom";
import api from "@/lib/api";
import SongOptionsMenu from "@/components/songs/SongOptionsMenu";
import { toast } from "sonner";

const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs.toString().padStart(2, "0")}`;
};

export default function PlaylistPage() {
  const { id } = useParams();
  const router = useRouter();
  const { playSong, playQueue, currentSong, isPlaying, togglePlay: toggleGlobalPlay } = usePlayer();
  const { likedSongIds, toggleLike, likedSongsLoading, likedPlaylistIds, togglePlaylistLike } = useUser();
  const [songs, setSongs] = useState<EachSongDTO[]>([]);
  const [playlist, setPlaylist] = useState<PlaylistPreviewDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);

  // Edit Modal State
  const [showEditModal, setShowEditModal] = useState(false);
  const [editName, setEditName] = useState("");
  const [editVisibility, setEditVisibility] = useState<PlaylistVisibility>(PlaylistVisibility.PUBLIC);
  const [editFile, setEditFile] = useState<File | null>(null);
  const [editPreviewUrl, setEditPreviewUrl] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [mounted, setMounted] = useState(false);
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setMounted(true);
    const authDetails = localStorage.getItem("auth_details");
    if (authDetails) {
      try {
        const user = JSON.parse(authDetails);
        setCurrentUserId(user.id);
      } catch (e) {
        console.error("Error parsing auth details", e);
      }
    }
  }, []);

  useEffect(() => {
    const fetchData = async () => {
      const playlistId = Array.isArray(id) ? id[0] : id;
      if (!playlistId) return;

      try {
        setLoading(true);
        const [playlistRes, songsRes] = await Promise.all([
          api.get(`/api/v1/playlist/${playlistId}`),
          api.get(`/api/v1/playlist/${playlistId}/songs`)
        ]);

        setPlaylist(playlistRes.data.data || playlistRes.data);
        setSongs(songsRes.data.data || songsRes.data || []);
      } catch (error) {
        console.error("Failed to fetch playlist data:", error);
        toast.error("Failed to load playlist");
        router.push("/library");
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id, router]);

  useEffect(() => {
    if (playlist?.name === "Liked Songs" && !likedSongsLoading) {
      setSongs((prevSongs) => prevSongs.filter((song) => likedSongIds.has(song.id)));
    }
  }, [likedSongIds, playlist, likedSongsLoading]);

  const handlePlaySong = (song: EachSongDTO) => {
    if (currentSong?.id === song.id) {
      toggleGlobalPlay();
      return;
    }

    if (!songs.length) return;

    const startIndex = songs.findIndex(s => s.id === song.id);
    if (startIndex === -1) {
      playSong(song);
      return;
    }

    playQueue(songs, startIndex);
  };

  const isCurrentSongPlaying = (songId: string) => {
    return currentSong?.id === songId && isPlaying;
  };

  const handleOpenEdit = () => {
    if (!playlist) return;
    setEditName(playlist.name);
    setEditVisibility(playlist.visibility || PlaylistVisibility.PUBLIC);
    setEditPreviewUrl(playlist.coverUrl);
    setEditFile(null);
    setShowEditModal(true);
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setEditFile(file);
      const url = URL.createObjectURL(file);
      setEditPreviewUrl(url);
    }
  };

  const handleSavePlaylist = async () => {
    if (!editName.trim()) {
      toast.error("Playlist name is required");
      return;
    }

    try {
      setIsSaving(true);
      const payload: UpsertPlaylistDTO = {
        id: playlist!.id,
        name: editName.trim(),
        visibility: editVisibility
      };

      await api.post("/api/v1/playlist/upsert", payload);

      if (editFile) {
        const formData = new FormData();
        formData.append("file", editFile, `playlist cover_url ${playlist!.id}`);
        await api.post("/api/v1/files", formData, {
          headers: { "Content-Type": "multipart/form-data" },
        });
      }

      toast.success("Playlist updated successfully");
      setShowEditModal(false);
      window.location.reload();
    } catch (error) {
      console.error("Failed to update playlist", error);
      toast.error("Failed to update playlist");
    } finally {
      setIsSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center text-white">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-red-500"></div>
      </div>
    );
  }

  if (!playlist) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center text-white">
        <p>Playlist not found</p>
      </div>
    );
  }

  const totalDuration = songs.reduce((acc, song) => acc + song.totalDuration, 0);
  const totalMinutes = Math.floor(totalDuration / 60);
  const totalSeconds = totalDuration % 60;
  const formattedTotalDuration = `${totalMinutes} min ${totalSeconds} sec`;

  return (
    <div className="min-h-screen text-white">
      {/* Header */}
      <div className="px-6 pt-16 pb-6 flex flex-col md:flex-row gap-6 items-end">
        <div className="w-56 h-56 relative shadow-2xl flex-shrink-0 bg-zinc-800 flex items-center justify-center rounded-lg overflow-hidden">
          {playlist.coverUrl || playlist.name === "Liked Songs" ? (
            <Image
              src={playlist.name === "Liked Songs" ? "/images/playlists/liked-songs.jpg" : playlist.coverUrl}
              alt={playlist.name}
              fill
              className="object-cover"
            />
          ) : (
            <Music className="w-20 h-20 text-zinc-600" />
          )}
        </div>
        <div className="flex flex-col justify-end w-full">
          <span className="text-sm font-bold uppercase tracking-wider mb-2">
            Playlist
          </span>
          <h1 className="text-5xl md:text-7xl font-bold mb-6 truncate">
            {playlist.name}
          </h1>
          <div className="flex items-center gap-2 text-sm text-gray-300">
            {playlist.owner && (
              <div className="flex items-center gap-1">
                {playlist.owner.profileUrl ? (
                  <div className="relative w-6 h-6 rounded-full overflow-hidden">
                    <Image 
                      src={playlist.owner.profileUrl} 
                      alt={playlist.owner.name}
                      fill
                      className="object-cover"
                    />
                  </div>
                ) : (
                  <div className="w-6 h-6 rounded-full bg-zinc-700 flex items-center justify-center">
                    <span className="text-xs font-bold text-white">
                      {playlist.owner.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
                <span className="font-bold text-white hover:underline cursor-pointer" onClick={() => router.push(`/profile/${playlist.owner.id}`)}>
                  {playlist.owner.name}
                </span>
              </div>
            )}
            <span>•</span>
            <span>{songs.length} songs, {formattedTotalDuration}</span>
            {playlist.visibility && (
              <>
                <span>•</span>
                <span className="bg-zinc-800 text-xs px-2 py-0.5 rounded-full border border-zinc-700">{playlist.visibility}</span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Controls */}
      <div className="px-6 py-6 sticky top-0 z-10">
        <div className="flex items-center gap-4">
          <button
            onClick={() => {
              if (songs.length > 0) {
                if (isCurrentSongPlaying(songs[0].id)) {
                  toggleGlobalPlay();
                } else {
                  handlePlaySong(songs[0]);
                }
              }
            }}
            className="w-14 h-14 rounded-full bg-red-500 hover:bg-red-400 hover:scale-105 transition flex items-center justify-center shadow-lg"
          >
            {songs.length > 0 && isCurrentSongPlaying(songs[0]?.id) ? (
              <Pause className="w-6 h-6 text-black fill-black" />
            ) : (
              <Play className="w-6 h-6 text-black fill-black ml-1" />
            )}
          </button>
          <button
            onClick={() => {
              if (songs.length === 0) return;
              const shuffled = [...songs];
              for (let i = shuffled.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
              }
              playQueue(shuffled, 0);
            }}
            className="w-10 h-10 border border-zinc-600 text-zinc-400 hover:border-white hover:text-white rounded-full flex items-center justify-center transition"
            title="Shuffle Play"
          >
            <Shuffle className="w-5 h-5" />
          </button>

          {currentUserId && playlist.owner.id !== currentUserId && (
            <button
              onClick={() => togglePlaylistLike(playlist.id)}
              className={`w-10 h-10 border rounded-full flex items-center justify-center transition ${
                likedPlaylistIds.has(playlist.id)
                  ? "border-red-500 text-red-500"
                  : "border-zinc-600 text-zinc-400 hover:border-white hover:text-white"
              }`}
              title={likedPlaylistIds.has(playlist.id) ? "Remove from Library" : "Save to Library"}
            >
              <Heart className={`w-5 h-5 ${likedPlaylistIds.has(playlist.id) ? "fill-red-500" : ""}`} />
            </button>
          )}
          
          {currentUserId && playlist.owner.id === currentUserId && playlist.name !== "Liked Songs" && (
            <button
              onClick={handleOpenEdit}
              className="w-10 h-10 border border-zinc-600 text-zinc-400 hover:border-white hover:text-white rounded-full flex items-center justify-center transition"
              title="Edit Playlist"
            >
              <Edit2 className="w-5 h-5" />
            </button>
          )}
        </div>
      </div>

      {/* Song List */}
      <div className="px-6 pb-12">
        <div className="grid grid-cols-[16px_1fr_auto_auto] gap-4 px-4 py-2 text-sm text-gray-400 border-b border-zinc-800 mb-2 sticky top-[88px] bg-black z-10">
          <div>#</div>
          <div>Title</div>
          <div className="flex justify-end">
            <Clock className="w-4 h-4" />
          </div>
          <div></div>
        </div>

        {songs.length === 0 ? (
          <div className="text-center py-12 text-zinc-500">
            This playlist is empty.
          </div>
        ) : (
          songs.map((song, index) => (
            <div
              key={song.id}
              onClick={() => handlePlaySong(song)}
              className="group grid grid-cols-[16px_1fr_auto_auto] gap-4 px-4 py-3 rounded-md hover:bg-white/10 transition cursor-pointer items-center"
            >
              <div className="flex items-center justify-center text-gray-400 w-4">
                {isCurrentSongPlaying(song.id) ? (
                  <div className="w-3 h-3 bg-red-500 rounded-full animate-pulse" />
                ) : (
                  <span className="group-hover:hidden">{index + 1}</span>
                )}
                <Play className="w-3 h-3 text-white hidden group-hover:block" fill="white" />
              </div>
              
              <div className="flex items-center gap-4 min-w-0">
                <div className="relative w-10 h-10 flex-shrink-0 bg-zinc-800 rounded overflow-hidden">
                  {song.recordPreviewWithCoverImageDTO?.coverUrl ? (
                    <Image 
                      src={song.recordPreviewWithCoverImageDTO.coverUrl} 
                      alt={song.title} 
                      fill 
                      className="object-cover"
                    />
                  ) : (
                    <Music className="w-full h-full p-2 text-zinc-600" />
                  )}
                </div>
                <div className="flex flex-col min-w-0">
                  <span className={`font-medium truncate ${isCurrentSongPlaying(song.id) ? "text-red-500" : "text-white"}`}>
                    {song.title}
                  </span>
                  <div className="flex items-center gap-1 text-sm text-gray-400 truncate">
                    {song.artists.map((artist, i) => (
                      <span key={artist.id} className="hover:text-white hover:underline" onClick={(e) => {
                        e.stopPropagation();
                        router.push(`/artists/${artist.id}`);
                      }}>
                        {artist.name}{i < song.artists.length - 1 ? ", " : ""}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-4 justify-end">
                <button
                  onClick={(e) => toggleLike(song.id, e)}
                  className={`transition ${likedSongIds.has(song.id) ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}
                >
                  <Heart
                    className={`w-4 h-4 ${likedSongIds.has(song.id)
                      ? "fill-red-500 text-red-500"
                      : "text-gray-400 hover:text-white"
                      }`}
                  />
                </button>
                <span className="text-sm text-gray-400 w-12 text-right">
                  {formatDuration(song.totalDuration)}
                </span>
              </div>
              <div className="flex items-center justify-end opacity-0 group-hover:opacity-100 transition" onClick={(e) => e.stopPropagation()}>
                <SongOptionsMenu songId={song.id} song={song} />
              </div>
            </div>
          ))
        )}
      </div>

      {/* Edit Modal */}
      {mounted && showEditModal && createPortal(
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4" onClick={() => setShowEditModal(false)}>
          <div className="bg-black border border-zinc-800 rounded-xl w-full max-w-md shadow-2xl flex flex-col overflow-hidden" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h3 className="text-lg font-bold">Edit Playlist</h3>
              <button 
                onClick={() => setShowEditModal(false)}
                className="text-zinc-400 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-6 space-y-6">
              {/* Cover Image Upload */}
              <div className="flex justify-center">
                <div 
                  onClick={() => fileInputRef.current?.click()}
                  className="w-40 h-40 bg-zinc-900 rounded-lg flex flex-col items-center justify-center cursor-pointer overflow-hidden border border-zinc-800 hover:border-zinc-600 transition group relative"
                >
                  {editPreviewUrl ? (
                    <Image src={editPreviewUrl} alt="Cover Preview" fill className="object-cover" />
                  ) : (
                    <Music className="w-12 h-12 text-zinc-600 mb-2" />
                  )}
                  <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition">
                    <Camera className="w-8 h-8 text-white" />
                  </div>
                  <input 
                    type="file" 
                    ref={fileInputRef} 
                    className="hidden" 
                    accept="image/*"
                    onChange={handleFileSelect}
                  />
                </div>
              </div>

              {/* Name Input */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-1">Name</label>
                <input
                  type="text"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="w-full bg-white border border-zinc-200 rounded-md px-3 py-2 text-black focus:outline-none focus:border-red-500 transition"
                  placeholder="Playlist Name"
                />
              </div>

              {/* Visibility Select */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-1">Visibility</label>
                <select
                  value={editVisibility}
                  onChange={(e) => setEditVisibility(e.target.value as PlaylistVisibility)}
                  className="w-full bg-white border border-zinc-200 rounded-md px-3 py-2 text-black focus:outline-none focus:border-red-500 transition appearance-none"
                >
                  <option value={PlaylistVisibility.PUBLIC}>Public</option>
                  <option value={PlaylistVisibility.PRIVATE}>Private</option>
                </select>
              </div>
            </div>

            <div className="p-4 border-t border-zinc-800 flex justify-end gap-3">
              <button
                onClick={() => setShowEditModal(false)}
                className="px-4 py-2 rounded-full text-sm font-bold hover:bg-zinc-800 transition"
                disabled={isSaving}
              >
                Cancel
              </button>
              <button
                onClick={handleSavePlaylist}
                disabled={isSaving || !editName.trim()}
                className="bg-white text-black px-6 py-2 rounded-full text-sm font-bold hover:scale-105 transition disabled:opacity-50 disabled:hover:scale-100"
              >
                {isSaving ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
