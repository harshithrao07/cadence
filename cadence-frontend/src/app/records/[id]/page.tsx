"use client";

import { useRecords } from "@/context/RecordContext";
import { useSongs } from "@/context/SongContext";
import { usePlayer } from "@/context/PlayerContext";
import { useUser } from "@/context/UserContext";
import { RecordPreviewDTO, RecordType } from "@/types/Record";
import { EachSongDTO } from "@/types/Song";
import { Play, Pause, Clock, Heart, Settings, Shuffle } from "lucide-react";
import Image from "next/image";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import AddRecordPage from "../add/page";
import api from "@/lib/api";
import { toast } from "sonner";
import SongOptionsMenu from "@/components/songs/SongOptionsMenu";

const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs.toString().padStart(2, "0")}`;
};

const formatDate = (timestamp: number) => {
  return new Date(timestamp).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
};

export default function RecordPage() {
  const { id } = useParams();
  const router = useRouter();
  const { getRecordById } = useRecords();
  const { fetchSongsByRecordId } = useSongs();
  const { playSong, playQueue, currentSong, isPlaying, togglePlay: toggleGlobalPlay } = usePlayer();
  const { isAdmin, likedSongIds, toggleLike } = useUser();
  const [liked, setLiked] = useState(false);
  const [songs, setSongs] = useState<EachSongDTO[]>([]);
  const [record, setRecord] = useState<RecordPreviewDTO>({
    id: "",
    title: "Loading...",
    coverUrl: "",
    releaseTimestamp: Date.now(),
    recordType: RecordType.ALBUM,
    recordArtists: [],
  });
  const [showUpdateModal, setShowUpdateModal] = useState(false);
  const [showSettingsMenu, setShowSettingsMenu] = useState(false);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    try {
      setLoading(true);
      const recordId = Array.isArray(id) ? id[0] : id;
      if (recordId) {
        const record = await getRecordById(recordId);
        if (record) {
          setRecord(record);
        }
        const songs = await fetchSongsByRecordId(recordId, true);
        if (songs) {
          setSongs(songs);
        }
      }
    } catch (error) {
      console.error("Failed to load record data:", error);
      toast.error("Failed to load record data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    fetchData();
  }, [id, getRecordById, fetchSongsByRecordId]);

  const totalDuration = songs.reduce(
    (acc, song) => acc + song.totalDuration,
    0
  );
  
  const formattedTotalDuration = formatDuration(totalDuration);

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

  const handleDeleteRecord = () => {
    const recordIdParam = Array.isArray(id) ? id[0] : id;
    if (!recordIdParam) return;

    toast("Are you sure you want to delete this record?", {
      description: "This action cannot be undone.",
      action: {
        label: "Delete",
        onClick: async () => {
          try {
            await api.delete(`/api/v1/record/delete/${recordIdParam}`);
            toast.success("Record deleted successfully");
            router.push("/");
          } catch (error) {
            console.error("Failed to delete record:", error);
            toast.error("Failed to delete record. Please try again.");
          }
        },
      },
      cancel: {
        label: "Cancel",
        onClick: () => {},
      },
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-red-500"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen text-white">
      {/* Header */}
      <div className="px-6 pt-16 pb-6 flex flex-col md:flex-row gap-6 items-end">
        <Image
          src={record.coverUrl || "/images/records/record-placeholder.png"}
          alt={record.title}
          className="w-56 h-56 shadow-2xl"
          width={224}
          height={224}
        />
        <div className="flex flex-col justify-end">
          <span className="text-sm font-bold uppercase tracking-wider mb-2">
            {record.recordType}
          </span>
          <h1 className="text-5xl md:text-7xl font-bold mb-6">
            {record.title}
          </h1>
          {songs.length > 0 && (
            <div className="flex flex-wrap gap-2 mb-6">
              {(() => {
                const uniqueGenres = Array.from(
                  new Map(
                    songs
                      .flatMap((s) => s.genres || [])
                      .map((g) => [g.id, g.type])
                  ).entries()
                );
                return uniqueGenres.map(([id, type]) => (
                  <span
                    key={id}
                    className="px-3 py-1 bg-white/10 hover:bg-white/20 rounded-full text-xs font-medium transition cursor-default"
                  >
                    {type}
                  </span>
                ));
              })()}
            </div>
          )}
          <div className="flex items-center gap-2 text-sm">
            <div className="font-medium flex flex-wrap gap-1">
              {record.recordArtists?.length > 0 ? (
                record.recordArtists.map((artist, index) => (
                  <span key={artist.id}>
                    <button
                      onClick={() => router.push(`/artists/${artist.id}`)}
                      className="text-white hover:text-red-400 transition underline"
                    >
                      {artist.name}
                    </button>
                    {index < record.recordArtists.length - 1 && ", "}
                  </span>
                ))
              ) : (
                <span>Unknown Artist</span>
              )}
            </div>
            <span>•</span>
            <span>{formatDate(record.releaseTimestamp)}</span>
            <span>•</span>
            <span>
              {songs.length} songs, {formattedTotalDuration}
            </span>
          </div>
        </div>
      </div>

      {/* Controls */}
      <div className="px-6 py-6">
        <div className="flex items-center gap-4">
          <button
            onClick={() => {
              if (songs.length > 0) {
                handlePlaySong(songs[0]);
              }
            }}
            className="w-14 h-14 rounded-full bg-red-500 hover:bg-red-400 hover:scale-105 transition flex items-center justify-center"
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
            className="w-10 h-10 border border-gray-600 text-gray-400 hover:border-white hover:text-white rounded-full flex items-center justify-center transition"
            title="Shuffle Play"
          >
            <Shuffle className="w-5 h-5" />
          </button>
          {isAdmin && (
            <div className="relative">
              <button
                onClick={() => setShowSettingsMenu((prev) => !prev)}
                className="w-10 h-10 flex items-center justify-center rounded-full transition hover:scale-110"
                title="Record settings"
              >
                <Settings className="w-7 h-7 text-gray-400 hover:text-white" />
              </button>
              {showSettingsMenu && (
                <div className="absolute right-0 mt-2 w-40 bg-black border border-zinc-700 rounded-md shadow-lg z-20">
                  <button
                    className="w-full text-left px-4 py-2 text-sm hover:bg-zinc-800"
                    onClick={() => {
                      setShowUpdateModal(true);
                      setShowSettingsMenu(false);
                    }}
                  >
                    Edit record
                  </button>
                  <button
                    className="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-zinc-800"
                    onClick={() => {
                      setShowSettingsMenu(false);
                      handleDeleteRecord();
                    }}
                  >
                    Delete record
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Song List */}
      <div className="px-6 pb-12">
        <div className="grid grid-cols-[16px_1fr_auto_auto_auto] gap-4 px-4 py-2 text-sm text-gray-400 border-b border-gray-800 mb-2">
          <div>#</div>
          <div>Title</div>
          <div></div>
          <div className="flex justify-end">
            <Clock className="w-4 h-4" />
          </div>
          <div></div>
        </div>

        {songs.map((song, index) => (
          <div
            key={song.id}
            className="grid grid-cols-[16px_1fr_auto_auto_auto] gap-4 px-4 py-3 rounded hover:bg-white/10 group cursor-pointer transition"
            onClick={() => handlePlaySong(song)}
          >
            <div className="flex items-center justify-center text-gray-400 group-hover:text-white">
              {isCurrentSongPlaying(song.id) ? (
                <div className="flex gap-0.5 items-end h-4">
                  <span className="w-0.5 bg-red-500 animate-pulse h-2"></span>
                  <span
                    className="w-0.5 bg-red-500 animate-pulse h-4"
                    style={{ animationDelay: "0.2s" }}
                  ></span>
                  <span
                    className="w-0.5 bg-red-500 animate-pulse h-3"
                    style={{ animationDelay: "0.4s" }}
                  ></span>
                </div>
              ) : (
                <span className="group-hover:hidden">{index + 1}</span>
              )}
              <Play className="w-4 h-4 hidden group-hover:block text-white fill-white" />
            </div>
            <div className="flex items-center gap-3">
              <Image
                src={record.coverUrl || "/images/records/record-placeholder.png"}
                alt={song.title}
                className="w-10 h-10 md:hidden"
                width={40}
                height={40}
              />
              <div>
                <div
                  className={`font-medium ${isCurrentSongPlaying(song.id) ? "text-red-500" : "text-white"
                    }`}
                >
                  {song.title}
                </div>
                <div className="text-sm text-gray-400">
                  {(() => {
                    const sortedArtists = song.artists || [];

                    if (sortedArtists.length === 0) return "Unknown Artist";

                    return sortedArtists.map((artist, artistIndex) => (
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
                        {artistIndex < sortedArtists.length - 1 && ", "}
                      </span>
                    ));
                  })()}
                </div>
              </div>
            </div>
            <div className="flex items-center justify-center">
              <button
                onClick={(e) => toggleLike(song.id, e)}
                className={`transition hover:scale-110 ${likedSongIds.has(song.id) ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}
              >
                <Heart
                  className={`w-5 h-5 ${likedSongIds.has(song.id)
                    ? "fill-red-500 text-red-500 opacity-100"
                    : "text-gray-400 hover:text-white"
                    }`}
                  style={likedSongIds.has(song.id) ? { opacity: 1 } : {}}
                />
              </button>
            </div>
            <div className="flex items-center justify-end text-gray-400 text-sm">
              {formatDuration(song.totalDuration)}
            </div>
            <div className="flex items-center justify-end opacity-0 group-hover:opacity-100 transition" onClick={(e) => e.stopPropagation()}>
              <SongOptionsMenu songId={song.id} song={song} />
            </div>
          </div>
        ))}

        {songs.length === 0 && (
          <div className="text-center py-12">
            <div className="text-gray-400 text-lg mb-2">No songs available</div>
            <div className="text-gray-500 text-sm">This record does not have any songs yet.</div>
          </div>
        )}
      </div>

      {/* Update Record Modal */}
      {showUpdateModal && (
        <div className="fixed inset-0 bg-black flex items-center justify-center z-50">
          <div className="bg-gray-900 rounded-lg max-w-6xl w-full max-h-[90vh] overflow-hidden">
            <div className="max-h-[calc(90vh-80px)] overflow-y-auto pb-24">
              <AddRecordPage
                isUpdate={true}
                existingRecord={record}
                existingSongs={songs}
                onUpdateSuccess={() => {
                  fetchData();
                  setShowUpdateModal(false);
                }}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
