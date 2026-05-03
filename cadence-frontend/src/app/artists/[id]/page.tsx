"use client";

import React, { useEffect, useState } from "react";
import {
  Play,
  Pause,
  Heart,
  MoreHorizontal,
  Shuffle,
  Disc,
  UserPlus,
  UserCheck,
  Users,
  X,
} from "lucide-react";
import { useParams } from "next/navigation";
import api from "@/lib/api";
import { ArtistProfileDTO } from "@/types/Artists";
import { RecordPreviewDTO } from "@/types/Record";
import { ApiResponseDTO } from "@/types/ApiResponse";
import Image from "next/image";
import { useArtists } from "@/context/ArtistContext";
import { useUser } from "@/context/UserContext";
import { useRouter } from "next/navigation";
import { usePlayer } from "@/context/PlayerContext";
import { EachSongDTO, TopSongsInArtistProfileDTO } from "@/types/Song";
import { toast } from "sonner";
import AddArtist from "../../../components/artists/AddArtist";
import { UserPreviewDTO } from "@/types/User";
import SongOptionsMenu from "@/components/songs/SongOptionsMenu";

export default function ArtistProfile() {
  const { getArtistById, refreshArtists } = useArtists();
  const { isAdmin, likedSongIds, toggleLike } = useUser();
  const { playSong, playQueue, currentSong, isPlaying: isGlobalPlaying, togglePlay: toggleGlobalPlay } = usePlayer();
  const { id } = useParams();
  const [artist, setArtist] = useState<ArtistProfileDTO>({
    id: "",
    name: "Unknown Artist",
    profileUrl: "",
    description: "This artist profile is loading.",
    followers: 0,
    monthlyListeners: 0,
    popularSongs: [],
    artistRecords: [],
  });
  const [popularSongs, setPopularSongs] = useState<TopSongsInArtistProfileDTO[]>([]);
  const [artistRecords, setArtistRecords] = useState<RecordPreviewDTO[]>([]);
  const router = useRouter();
  const [showMoreMenu, setShowMoreMenu] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [isFollowing, setIsFollowing] = useState(false);
  const [showFollowers, setShowFollowers] = useState(false);
  const [followersList, setFollowersList] = useState<UserPreviewDTO[]>([]);
  const [loadingFollowers, setLoadingFollowers] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    const artistId = Array.isArray(id) ? id[0] : id;
    const preview = getArtistById(artistId);
    if (preview) {
      setArtist((prev) => ({
        ...prev,
        id: preview.id,
        name: preview.name,
        profileUrl: preview.profileUrl || prev.profileUrl,
      }));
    }

    const loadData = async () => {
      try {
        setLoading(true);
        // Fetch artist data based on id
        const fetchArtistData = async (artistId) => {
            const res = await api.get<ApiResponseDTO<ArtistProfileDTO>>(
            "/api/v1/artist/" + artistId
            );
            setArtist(res.data.data);
            setPopularSongs(res.data.data.popularSongs);
            setArtistRecords(res.data.data.artistRecords);
        };

        const checkIsFollowing = async (artistId: string) => {
            const res = await api.get(`/api/v1/artist/${artistId}/isFollowing`);
            // Assuming the API returns a boolean directly or wrapped in data
            setIsFollowing(!!res.data.data);
        };

        await Promise.all([fetchArtistData(artistId), checkIsFollowing(artistId)]);
      } catch (error) {
        console.error("Failed to fetch artist data:", error);
        toast.error("Failed to load artist data");
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [id]);

  const handleFollowToggle = async () => {
    const artistIdParam = Array.isArray(id) ? id[0] : id;
    if (!artistIdParam) return;

    try {
      if (isFollowing) {
        await api.post(`/api/v1/artist/${artistIdParam}/unfollow`);
        setIsFollowing(false);
        setArtist((prev) => ({ ...prev, followers: Math.max(0, prev.followers - 1) }));
        toast.success("Unfollowed artist");
      } else {
        await api.post(`/api/v1/artist/${artistIdParam}/follow`);
        setIsFollowing(true);
        setArtist((prev) => ({ ...prev, followers: prev.followers + 1 }));
        toast.success("Following artist");
      }
    } catch (e) {
      console.error("Failed to toggle follow", e);
      toast.error("Failed to update follow status");
    }
  };

  const fetchFollowers = async () => {
    const artistIdParam = Array.isArray(id) ? id[0] : id;
    if (!artistIdParam) return;

    setLoadingFollowers(true);
    try {
      const res = await api.get(`/api/v1/artist/${artistIdParam}/followers`);
      setFollowersList(res.data.data || []);
    } catch (e) {
      console.error("Failed to fetch followers", e);
      toast.error("Failed to load followers");
    } finally {
      setLoadingFollowers(false);
    }
  };

  const formatReleaseDate = (timestamp: string | number) => {
    const date = new Date(Number(timestamp));
    return date.getFullYear();
  };

  const formatDuration = (seconds: number | string) => {
    const totalSeconds = typeof seconds === "string" ? parseInt(seconds) : seconds;
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const isCurrentSongPlaying = (songId: string) => {
    return currentSong?.id === songId && isGlobalPlaying;
  };

  const handlePlayPopularSong = (track: TopSongsInArtistProfileDTO) => {
    if (currentSong?.id === track.id) {
      toggleGlobalPlay();
      return;
    }

    if (!popularSongs.length) return;

    const orderedTracks = [...popularSongs];

    const queueSongs: EachSongDTO[] = orderedTracks.map((t) => ({
      id: t.id,
      title: t.title,
      totalDuration: t.totalDuration,
      artists: t.artists,
      genres: [],
      recordPreviewWithCoverImageDTO: {
        id: t.recordId,
        title: t.recordTitle,
        coverUrl: t.coverUrl,
      },
    }));

    const startIndex = queueSongs.findIndex(s => s.id === track.id);
    if (startIndex === -1) {
      const songToPlay: EachSongDTO = {
        id: track.id,
        title: track.title,
        totalDuration: track.totalDuration,
        artists: track.artists,
        genres: [],
        recordPreviewWithCoverImageDTO: {
          id: track.recordId,
          title: track.recordTitle,
          coverUrl: track.coverUrl,
        },
      };
      playSong(songToPlay);
      return;
    }

    playQueue(queueSongs, startIndex);
  };

  const handleDeleteArtist = () => {
    const artistIdParam = Array.isArray(id) ? id[0] : id;
    if (!artistIdParam) return;

    toast("Are you sure you want to delete this artist?", {
      description: "This action cannot be undone.",
      action: {
        label: "Delete",
        onClick: async () => {
          try {
            await api.delete(`/api/v1/artist/delete/${artistIdParam}`);
            await refreshArtists();
            toast.success("Artist deleted successfully");
            router.push("/artists");
          } catch (error) {
            console.error("Failed to delete artist:", error);
            toast.error("Failed to delete artist. Please try again.");
          }
        },
      },
      cancel: {
        label: "Cancel",
        onClick: () => {},
      },
    });
  };

  if (isEditing) {
    return (
      <div className="min-h-screen bg-black text-white px-8 pt-16">
        <AddArtist
          setShowAddForm={setIsEditing}
          initialArtist={artist}
          mode="edit"
          onArtistSaved={(updated) => {
            setArtist((prev) => ({
              ...prev,
              name: updated.name,
              description: updated.description || "",
              profileUrl: updated.profileUrl ?? prev.profileUrl,
            }));
          }}
        />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-red-500"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen text-white">
      {/* Hero Section */}
      <div className="relative h-96">
        <div className="absolute inset-0 flex items-end p-8">
          {artist.profileUrl && (
            <Image
              src={artist.profileUrl || "/images/artists/artist-placeholder.png"}
              alt={artist.name}
              width={192}
              height={192}
              className="w-48 h-48 rounded-full object-cover shadow-2xl mr-6"
            />
          )}

          <div className="mb-4">
            <div className="flex items-center gap-2 mb-2">
              <svg
                className="w-6 h-6 text-blue-500"
                fill="currentColor"
                viewBox="0 0 24 24"
              >
                <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span className="text-sm">Verified Artist</span>
            </div>
            <h1 className="text-7xl font-bold mb-4">{artist.name}</h1>
            <div className="flex items-center gap-4 text-sm text-gray-300">
              <span>{artist.monthlyListeners} monthly listeners</span>
              <span className="w-1 h-1 bg-gray-500 rounded-full"></span>
              <button 
                onClick={() => {
                  setShowFollowers(true);
                  fetchFollowers();
                }}
                className="hover:text-white hover:underline transition flex items-center gap-1"
              >
                {artist.followers} followers
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Controls */}
      <div className="p-8">
        <div className="flex items-center justify-between mb-8">
          {/* Left controls */}
          <div className="flex items-center gap-6">
            <button
              onClick={() => {
                if (popularSongs.length > 0) {
                  if (isCurrentSongPlaying(popularSongs[0].id)) {
                    toggleGlobalPlay();
                  } else {
                    handlePlayPopularSong(popularSongs[0]);
                  }
                }
              }}
              className="w-14 h-14 bg-red-500 rounded-full flex items-center justify-center hover:bg-red-600 hover:scale-105 transition"
            >
              {popularSongs.length > 0 && isCurrentSongPlaying(popularSongs[0].id) ? (    
                <Pause fill="black" className="text-black" />
              ) : (
                <Play fill="black" className="text-black ml-1" />
              )}
            </button>

            <button
              onClick={handleFollowToggle}
              className={`h-10 px-6 rounded-full flex items-center gap-2 font-bold transition border ${
                isFollowing 
                  ? "border-zinc-600 text-white hover:border-zinc-400" 
                  : "border-transparent bg-white text-black hover:scale-105"
              }`}
            >
              {isFollowing ? (
                <>
                  <UserCheck className="w-4 h-4" />
                  Following
                </>
              ) : (
                <>
                  <UserPlus className="w-4 h-4" />
                  Follow
                </>
              )}
            </button>

            <button
              onClick={() => {
                if (popularSongs.length === 0) return;
                const shuffled = [...popularSongs];
                for (let i = shuffled.length - 1; i > 0; i--) {
                  const j = Math.floor(Math.random() * (i + 1));
                  [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
                }
                const queueSongs: EachSongDTO[] = shuffled.map((t) => ({
                  id: t.id,
                  title: t.title,
                  totalDuration: t.totalDuration,
                  artists: t.artists,
                  genres: [],
                  recordPreviewWithCoverImageDTO: {
                    id: t.recordId,
                    title: t.recordTitle,
                    coverUrl: t.coverUrl,
                  },
                }));
                playQueue(queueSongs, 0);
              }}
              className="w-10 h-10 border border-gray-600 text-gray-400 hover:border-white hover:text-white rounded-full flex items-center justify-center transition"
              title="Shuffle Play"
            >
              <Shuffle className="w-5 h-5" />
            </button>

            <div className="relative">
              <button
                className="w-10 h-10 flex items-center justify-center hover:scale-110 transition"
                onClick={() => setShowMoreMenu((prev) => !prev)}
              >
                <MoreHorizontal className="w-6 h-6" />
              </button>
              {isAdmin && showMoreMenu && (
                <div className="absolute right-0 mt-2 w-40 bg-black border border-zinc-700 rounded-md shadow-lg z-20">
                  <button
                    className="w-full text-left px-4 py-2 text-sm text-zinc-200 hover:bg-zinc-800"
                    onClick={() => {
                      setShowMoreMenu(false);
                      setIsEditing(true);
                    }}
                  >
                    Edit artist
                  </button>
                  <button
                    className="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-zinc-800"
                    onClick={() => {
                      setShowMoreMenu(false);
                      handleDeleteArtist();
                    }}
                  >
                    Delete artist
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Right action */}
          {isAdmin && (
            <button
              onClick={() => router.push(`/records/add?artistId=${artist.id}`)}
              className="flex items-center gap-2 bg-gradient-to-r from-zinc-800 to-zinc-700 hover:from-zinc-700 hover:to-zinc-600 px-6 py-3 rounded-full font-bold transition-all hover:scale-105 border border-zinc-700 shadow-lg"
            >
              <Disc className="w-5 h-5" />
              Add New Record
            </button>
          )}
        </div>

        {/* Popular Tracks */}
        <div>
          <h2 className="text-2xl font-bold mb-6">Popular</h2>
          <div className="space-y-2">
            {popularSongs.map((track, index) => (
              <div
                key={track.id}
                className="grid grid-cols-[auto_auto_1fr_auto_auto] gap-4 items-center p-2 rounded hover:bg-white/10 group transition"
              >
                <div className="w-8 text-gray-400 group-hover:hidden text-center flex items-center justify-center">
                  {isCurrentSongPlaying(track.id) ? (
                    <div className="flex gap-0.5 items-end h-3">
                      <span className="w-0.5 bg-red-500 animate-pulse h-1.5"></span>
                      <span className="w-0.5 bg-red-500 animate-pulse h-3" style={{ animationDelay: "0.2s" }}></span>
                      <span className="w-0.5 bg-red-500 animate-pulse h-2" style={{ animationDelay: "0.4s" }}></span>
                    </div>
                  ) : (
                    index + 1
                  )}
                </div>
                <button
                  onClick={() => handlePlayPopularSong(track)}
                  className="w-8 hidden group-hover:flex justify-center"
                >
                  {isCurrentSongPlaying(track.id) ? (
                    <Pause fill="white" className="w-4 h-4" />
                  ) : (
                    <Play fill="white" className="w-4 h-4" />
                  )}
                </button>
                <div className="min-w-0 flex items-center gap-3">
                  <Image
                    src={track.coverUrl || "/images/records/record-placeholder.png"}
                    alt={track.title}
                    width={40}
                    height={40}
                    className="w-10 h-10 rounded object-cover flex-shrink-0"
                  />
                  <div>
                    <div className={`font-semibold truncate ${isCurrentSongPlaying(track.id) ? "text-red-500" : "text-white"}`}>  
                      {track.title}
                    </div>
                    <div className="text-xs text-gray-400 mt-0.5">
                      {track.artists?.length > 0 ? (
                        track.artists.map((artist, artistIndex) => (
                          <span key={artist.id}>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                router.push(`/artists/${artist.id}`);
                              }}
                              className="hover:text-red-400 transition underline"
                            >
                              {artist.name}
                            </button>
                            {artistIndex < track.artists.length - 1 && ", "}
                          </span>
                        ))
                      ) : (
                        "Unknown Artist"
                      )}
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-4 justify-end">
                  <button
                  onClick={(e) => toggleLike(track.id, e)}
                  className={`transition ${likedSongIds.has(track.id) ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}
                >
                  <Heart
                    className={`w-5 h-5 ${likedSongIds.has(track.id)
                      ? "fill-red-500 text-red-500"
                      : "text-gray-400"
                      }`}
                  />
                </button>
                  <div className="text-sm text-gray-400 w-16 text-right">
                    {formatDuration(track.totalDuration)}
                  </div>
                </div>
                <div className="flex items-center justify-end opacity-0 group-hover:opacity-100 transition" onClick={(e) => e.stopPropagation()}>
                  <SongOptionsMenu songId={track.id} song={{
                    id: track.id,
                    title: track.title,
                    totalDuration: track.totalDuration,
                    artists: track.artists,
                    genres: [],
                    recordPreviewWithCoverImageDTO: {
                      id: track.recordId,
                      title: track.recordTitle,
                      coverUrl: track.coverUrl,
                    }
                  }} />
                </div>
              </div>
            ))}

            {popularSongs.length === 0 && (
              <div className="text-center py-12">
                <div className="text-gray-400 text-lg mb-2">No popular songs</div>
                <div className="text-gray-500 text-sm">This artist does not have any popular songs yet.</div>
              </div>
            )}
          </div>
        </div>

        {/* Discography */}
        <div className="mt-16">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-2xl font-bold">Discography</h2>
            <button
              onClick={() => router.push(`/records?artistId=${artist.id}`)}
              className="text-sm text-gray-400 hover:text-white transition"
            >
              Show all
            </button>
          </div>

          {artistRecords.length === 0 ? (
            <p className="text-gray-400">No records released yet.</p>
          ) : (
            <div className="grid grid-cols-3 md:grid-cols-5 lg:grid-cols-7 gap-4">
              {artistRecords.map((record) => (
                <div
                  key={record.id}
                  onClick={() => router.push(`/records/${record.id}`)}
                  className="group cursor-pointer"
                >
                  <div className="relative mb-2">
                    <Image
                      src={record.coverUrl || "/images/records/record-placeholder.png"}
                      alt={record.title}
                      width={150}
                      height={150}
                      className="w-full aspect-square rounded-lg object-cover shadow-lg group-hover:scale-105 transition"
                    />

                    {/* Play button overlay */}
                    <button className="absolute bottom-2 right-2 w-10 h-10 bg-red-500 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 translate-y-2 group-hover:translate-y-0 transition">
                      <Play fill="black" className="ml-0.5 w-4 h-4" />
                    </button>
                  </div>

                  <div className="font-semibold truncate text-sm">{record.title}</div>
                  <div className="text-xs text-gray-400">
                    {formatReleaseDate(record.releaseTimestamp)} • {record.recordType}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* About Section */}
        <div className="mt-12">
          <h2 className="text-2xl font-bold mb-4">About</h2>
          <div className="flex items-start gap-6">
            {artist.profileUrl && (
              <Image
                src={artist.profileUrl || "/images/artists/artist-placeholder.png"}
                alt={artist.name}
                width={192}
                height={192}
                className="w-48 h-48 rounded-lg object-cover"
              />
            )}

            <div>
              <div className="text-gray-300 mb-4">{artist.description}</div>
              <div className="text-sm font-semibold mb-1">
                {artist.followers} Followers
              </div>
            </div>
          </div>
        </div>
      </div>
      {/* Followers Modal */}
      {showFollowers && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4" onClick={() => setShowFollowers(false)}>
          <div className="bg-black border border-zinc-800 rounded-2xl w-full max-w-md max-h-[80vh] flex flex-col shadow-2xl animate-in fade-in zoom-in duration-200" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between p-6 border-b border-zinc-800">
              <h3 className="text-xl font-bold flex items-center gap-2">
                <Users className="w-5 h-5 text-red-500" />
                Followers
              </h3>
              <button 
                onClick={() => setShowFollowers(false)}
                className="text-zinc-400 hover:text-white transition rounded-full hover:bg-zinc-800 p-2"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="overflow-y-auto p-2 flex-1 custom-scrollbar">
              {loadingFollowers ? (
                <div className="flex justify-center py-8">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-red-500"></div>
                </div>
              ) : followersList.length > 0 ? (
                <div className="space-y-1">
                  {followersList.map((follower) => (
                    <div key={follower.id} className="flex items-center gap-3 p-3 hover:bg-zinc-800/50 rounded-xl transition cursor-pointer" onClick={() => router.push(`/profile/${follower.id}`)}>
                      <div className="relative w-10 h-10 rounded-full overflow-hidden bg-zinc-800 flex-shrink-0">
                        {follower.profileUrl ? (
                          <Image 
                            src={follower.profileUrl} 
                            alt={follower.name} 
                            fill 
                            className="object-cover"
                          />
                        ) : (
                          <div className="flex items-center justify-center h-full w-full text-zinc-500 font-bold bg-zinc-800">
                            {follower.name.charAt(0).toUpperCase()}
                          </div>
                        )}
                      </div>
                      <div className="font-medium truncate flex-1">{follower.name}</div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-12 text-zinc-500">
                  No followers yet
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
