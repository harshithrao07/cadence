"use client";

import React, { useEffect, useState, useCallback, useRef } from "react";
import { Trash2, Music, Edit2, Play, Pause, ArrowUp, ArrowDown } from "lucide-react";
import { GenreSelector } from "../genres/GenreSelector";
import { SongFilePicker } from "./SongFIlePicker";
import { RECORD_LIMITS } from "@/types/Record";
import { toast } from "sonner";
import { ArtistSelector } from "../artists/ArtistSelector";
import { UpsertSongDTO } from "@/types/Song";
import { RecordType } from "@/types/Record";
import { ArtistPreviewDTO } from "@/types/Artists";
import { useGenres } from "@/context/GenreContext";
import { usePlayer } from "@/context/PlayerContext";

export interface EditableSong extends UpsertSongDTO {
  totalDuration: number;
  order: number;
  coverUrl: string;
}

const AddSongSection = ({
  selectedArtists = [],
  recordType,
  recordTitle = "",
  onSongsChange,
  onSongFilesChange,
  initialSongs = [],
  isUpdate = false,
  fullExistingSongs = [], // Array of EachSongDTO objects for update mode
}) => {
  const recordArtistIds = React.useMemo(() => selectedArtists.map(a => a.id), [selectedArtists]);

  const [songs, setSongs] = useState<EditableSong[]>(initialSongs);
  const [songFiles, setSongFiles] = useState<Map<string, File>>(new Map());
  const [currentFile, setCurrentFile] = useState<File | undefined>(undefined);
  const [song, setSong] = useState<EditableSong>({
    title: "",
    genreIds: [],
    artistIds: [...recordArtistIds],
    totalDuration: 0,
    order: 1,
    coverUrl: ""
  });
  // Editing state
  const [editingSongIndex, setEditingSongIndex] = useState<number | null>(null);

  // Selector keys to force re-render when resetting
  const [artistSelectorKey, setArtistSelectorKey] = useState(0);
  const [genreSelectorKey, setGenreSelectorKey] = useState(0);
  const [filePickerKey, setFilePickerKey] = useState(0);

  // Track ALL artists encountered (record + featured) for lookup when displaying songs
  const [allEncounteredArtists, setAllEncounteredArtists] = useState<Map<string, ArtistPreviewDTO>>(new Map());

  const maxSongsAllowed = RECORD_LIMITS[recordType] ?? Infinity;
  const hasReachedLimit = songs.length >= maxSongsAllowed;
  const hasGenre = song.genreIds.length > 0;

  const { playQueue, currentSong, isPlaying, togglePlay } = usePlayer();

  // Track previous recordArtistIds to identify featured artists during sync
  const prevRecordArtistIdsRef = useRef<string[]>(recordArtistIds);

  // Sync Record Title to first song if RecordType.SINGLE
  useEffect(() => {
    if (recordType === RecordType.SINGLE && recordTitle.trim()) {
      setSongs(prevSongs => {
        if (prevSongs.length === 0) return prevSongs;
        const newSongs = [...prevSongs];
        newSongs[0] = { ...newSongs[0], title: recordTitle.trim() };
        return newSongs;
      });
    }
  }, [recordTitle, recordType]);

  // Sync Record Artists to ALL songs
  useEffect(() => {
    const prevIds = prevRecordArtistIdsRef.current;

    const removedRecordIds = prevIds.filter(id => !recordArtistIds.includes(id));
    const newRecordIds = recordArtistIds.filter(id => !prevIds.includes(id));

    setSongs(prevSongs => {
      if (prevSongs.length === 0) return prevSongs;

      return prevSongs.map(s => {
        let newArtistIds = s.artistIds.filter(id => !removedRecordIds.includes(id));

        newRecordIds.forEach(id => {
          if (!newArtistIds.includes(id)) {
            newArtistIds.push(id);
          }
        });

        return {
          ...s,
          artistIds: newArtistIds
        };
      });
    });

    prevRecordArtistIdsRef.current = recordArtistIds;
  }, [recordArtistIds]);

  const [deletedSongIds, setDeletedSongIds] = useState<string[]>([]);

  useEffect(() => {
    onSongsChange?.(songs, deletedSongIds);
  }, [songs, deletedSongIds]);

  useEffect(() => {
    onSongFilesChange?.(songFiles);
  }, [songFiles]);

  useEffect(() => {
    if (editingSongIndex === null) {
      setSong((prev) => ({
        ...prev,
        artistIds: recordArtistIds,
      }));
    }
  }, [recordArtistIds, editingSongIndex]);

  useEffect(() => {
    if (recordType === RecordType.SINGLE && recordTitle.trim() && editingSongIndex === null) {
      setSong((prev) => ({
        ...prev,
        title: recordTitle.trim(),
      }));
    }
  }, [recordTitle, recordType, editingSongIndex]);

  const { genres, fetchGenres } = useGenres();

  // Fetch genres on mount
  useEffect(() => {
    if (genres.length === 0) {
      fetchGenres();
    }
  }, []);

  // Update allEncounteredArtists when selectedArtists change
  useEffect(() => {
    setAllEncounteredArtists(prevMap => {
      const newMap = new Map(prevMap);
      let changed = false;

      // Add record artists (these are passed as objects from parent)
      selectedArtists.forEach(artist => {
        if (!newMap.has(artist.id)) {
          newMap.set(artist.id, artist);
          changed = true;
        }
      });

      return changed ? newMap : prevMap;
    });
  }, [selectedArtists]);

  // Update allEncounteredArtists when fullExistingSongs change (for featured artists in update mode)
  useEffect(() => {
    if (!isUpdate || !fullExistingSongs.length) return;

    setAllEncounteredArtists(prevMap => {
      const newMap = new Map(prevMap);
      let changed = false;

      fullExistingSongs.forEach(song => {
        song.artists?.forEach(artist => {
          if (!newMap.has(artist.id)) {
            newMap.set(artist.id, artist);
            changed = true;
          }
        });
      });

      return changed ? newMap : prevMap;
    });
  }, [fullExistingSongs, isUpdate]);

  const resetForm = () => {
    setSong({
      title: recordType === RecordType.SINGLE ? recordTitle.trim() : "",
      genreIds: [],
      artistIds: [...recordArtistIds],
      totalDuration: 0,
      order: recordType === RecordType.SINGLE ? 0 :
        Math.max(...songs.map(s => s.order), -1) + 1,
      coverUrl: ""
    });
    setCurrentFile(undefined);
    setEditingSongIndex(null);

    setArtistSelectorKey((k) => k + 1);
    setGenreSelectorKey((k) => k + 1);
    setFilePickerKey((k) => k + 1);
  };

  const handleSaveSong = () => {
    // Validation
    const isEditing = editingSongIndex !== null;

    // For updates, we might not need a new file if we keep the old one
    if (!currentFile && !isEditing) {
      toast.error("Please upload an audio file");
      return;
    }

    // If editing, we need either a new file OR an existing file for this song
    if (isEditing && !currentFile && !songFiles.has(song.title.trim())) {
      // Only error if we somehow lost the file mapping and didn't provide a new one
      // But usually we key by title. If title changed, we check if we have a file.
      // Actually, let's simplify: if we are editing, currentFile is optional ONLY IF we aren't changing the title/file in a way that loses data
      // For simplicity: if currentFile is undefined, we assume we keep the old file associated with the OLD title (before rename).
      // Logic handled below.
    }

    if (!song.title.trim()) {
      toast.error("Song title is required");
      return;
    }

    const trimmedTitle = song.title.trim();

    // Check availability
    // If adding: invalid if exists in current songs or file map
    // If editing: invalid if exists in OTHER songs (excluding self)
    const titleConflict = songs.some((s, idx) =>
      s.title.trim() === trimmedTitle && idx !== editingSongIndex
    );

    if (titleConflict) {
      toast.error(
        "A song with this title already exists. Please use a different title."
      );
      return;
    }

    if (song.genreIds.length === 0) {
      toast.error("Please select at least one genre");
      return;
    }

    if (song.totalDuration > 600) {
      toast.error("Song duration cannot exceed 10 minutes");
      return;
    }

    // Limit check only if adding new song
    if (!isEditing && hasReachedLimit) {
      toast.error(
        recordType === "SINGLE"
          ? "A Single can contain only one song"
          : `You can add up to ${maxSongsAllowed} songs for an ${recordType}`
      );
      return;
    }

    if (currentFile) {
      setSongFiles(prev => {
        const newMap = new Map(prev);
        if (isEditing) {
          const oldTitle = songs[editingSongIndex].title.trim();
          newMap.delete(oldTitle);
        }
        newMap.set(trimmedTitle, currentFile);
        return newMap;
      });
    } else if (isEditing) {
      const oldTitle = songs[editingSongIndex].title.trim();
      if (oldTitle !== trimmedTitle) {
        setSongFiles(prev => {
          const newMap = new Map(prev);
          const file = newMap.get(oldTitle);
          if (file) {
            newMap.delete(oldTitle);
            newMap.set(trimmedTitle, file);
          }
          return newMap;
        });
      }
    }

    setSongs((prev) => {
      const isEditingCurrent = editingSongIndex !== null;
      const updatedSong = { ...song, title: trimmedTitle };
      let newSongs: EditableSong[] = [];

      if (isEditingCurrent && editingSongIndex !== null) {
        newSongs = prev.map((s, idx) =>
          idx === editingSongIndex ? updatedSong : s
        );
      } else {
        newSongs = [...prev, updatedSong];
      }

      return newSongs.map((s, idx) => ({
        ...s,
        order: idx,
      }));
    });

    toast.success(isEditing ? "Song updated successfully" : "Song added");
    resetForm();
  };

  const startEditingSong = (index: number) => {
    const songToEdit = songs[index];
    setEditingSongIndex(index);
    setSong({
      id: songToEdit.id,
      title: songToEdit.title,
      genreIds: [...songToEdit.genreIds],
      artistIds: [...songToEdit.artistIds],
      totalDuration: songToEdit.totalDuration,
      order: songToEdit.order,
      coverUrl: songToEdit.coverUrl,
    });

    // We don't set currentFile here because we don't have the File object easily accessible 
    // unless we fetch it from songFiles. 
    // AND `currentFile` state implies "New File Selected".
    // If we want to show the file name, we rely on the component knowing we are in edit mode
    // and using the existing file if `currentFile` is null.
    // The `SongFilePicker` needs to handle this visually if we want it to show "Current file: ..."

    // Let's retrieve the file if possible to pass to picker? 
    // The picker accepts `selectedFile`.
    setCurrentFile(songFiles.get(songToEdit.title.trim()));

    // Restore featured artist IDs (artists that are not record artists)
    const featuredIds = songToEdit.artistIds.filter(
      id => !recordArtistIds.includes(id)
    );

    // Force re-render of components to reflect new state

    setArtistSelectorKey(prev => prev + 1);
    setGenreSelectorKey(prev => prev + 1);
    setFilePickerKey(prev => prev + 1);
  };

  const removeSong = async (index: number) => {
    if (editingSongIndex === index) {
      resetForm();
    }

    const songToRemove = songs[index] as EditableSong & { id?: string };
    if (songToRemove.id) {
      setDeletedSongIds(prev => [...prev, songToRemove.id!]);
    }

    setSongs((prev) => {
      const target = prev[index];

      if (target) {
        setSongFiles((fileMap) => {
          const newMap = new Map(fileMap);
          newMap.delete(target.title.trim());
          return newMap;
        });
      }

      return prev
        .filter((_, i) => i !== index)
        .map((song, idx) => ({
          ...song,
          order: idx,
        }));
    });
  };

  const moveSongUp = (index: number) => {
    if (index <= 0) return;
    setSongs((prev) => {
      const newSongs = [...prev];
      const temp = newSongs[index - 1];
      newSongs[index - 1] = newSongs[index];
      newSongs[index] = temp;
      return newSongs.map((song, idx) => ({
        ...song,
        order: idx,
      }));
    });
  };

  const moveSongDown = (index: number) => {
    if (index >= songs.length - 1) return;
    setSongs((prev) => {
      const newSongs = [...prev];
      const temp = newSongs[index + 1];
      newSongs[index + 1] = newSongs[index];
      newSongs[index] = temp;
      return newSongs.map((song, idx) => ({
        ...song,
        order: idx,
      }));
    });
  };

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  const canSave =
    song.title.trim() &&
    hasGenre &&
    song.totalDuration <= 600 &&
    (editingSongIndex !== null || (!hasReachedLimit && currentFile)); // If adding, need file + limit check. If editing, file is optional (keep existing)

  const playFromAddSection = (songIndex: number) => {
    const playableSongs: any[] = [];
    let targetIndexInQueue = 0;

    songs.forEach((s, idx) => {
      const file = songFiles.get(s.title.trim());
      if (!file) return;

      const artistsForSong = s.artistIds
        .map(id => allEncounteredArtists.get(id))
        .filter(a => Boolean(a)) as ArtistPreviewDTO[];

      const dto = {
        id: `local-${idx}`,
        title: s.title,
        totalDuration: s.totalDuration,
        recordPreviewWithCoverImageDTO: {
          id: "local-record",
          title: recordTitle || "New Record",
          coverUrl: s.coverUrl,
        },
        createdBy: [],
        songUrl: "",
        order: s.order,
        artists: artistsForSong,
        genres: [],
      };

      (dto as any).file = file;

      if (idx === songIndex) {
        targetIndexInQueue = playableSongs.length;
      }

      playableSongs.push(dto);
    });

    if (playableSongs.length === 0) return;

    playQueue(playableSongs, targetIndexInQueue);
  };

  const getArtistNames = (artistIds: string[]) => {
    // Use the persistent map of all encountered artists
    const names = artistIds
      .map(id => {
        const artist = allEncounteredArtists.get(id);
        return artist?.name;
      })
      .filter(Boolean)
      .join(", ") || "Unknown Artist";
    return names;
  };

  const getGenreNames = (genreIds: string[]) => {
    return genreIds
      .map(id => genres.find(genre => genre.id === id)?.type)
      .filter(Boolean)
      .join(", ") || "No genre";
  };

  return (
    <div className="min-h-screen bg-black text-gray-100 p-8">
      <div className="max-w-4xl mx-auto">
        <div className="mb-8">
          <h1 className="text-4xl font-bold mb-2">Add Songs</h1>
          <p className="text-gray-300">Upload and manage your music library</p>
        </div>

        <div className="bg-gray-800/80 rounded-2xl p-8 backdrop-blur-sm border border-gray-700">
          <h2 className="text-xl font-bold mb-4 text-white">
            {editingSongIndex !== null ? "Edit Song" : "Add New Song"}
          </h2>

          <div className="mb-6">
            <label className="block text-sm font-semibold mb-3 text-gray-100">
              Song Title
            </label>
            <input
              placeholder={
                recordType === RecordType.SINGLE
                  ? "Same as record title"
                  : "Enter song title..."
              }
              value={song.title}
              onChange={(e) => {
                if (recordType !== RecordType.SINGLE) {
                  setSong({ ...song, title: e.target.value });
                }
              }}
              disabled={recordType === RecordType.SINGLE}
              className="w-full bg-gray-800 text-gray-100 px-4 py-3 rounded-lg border border-gray-700 focus:border-green-500 focus:outline-none transition-colors placeholder:text-gray-400 disabled:opacity-50 disabled:cursor-not-allowed"
            />
            {recordType === RecordType.SINGLE && (
              <p className="mt-2 text-sm text-gray-300">
                Song title will be the same as the record title
              </p>
            )}
          </div>

          
          <ArtistSelector
            key={`artist-${artistSelectorKey}`}
            selectedArtistIds={song.artistIds}
            setSelectedArtistIds={(ids) => {
              setSong((prev) => ({ ...prev, artistIds: ids }));
            }}
            onArtistsChange={useCallback((artists: ArtistPreviewDTO[]) => {
              // Ensure all selected artists are our lookup map

              setAllEncounteredArtists(prev => {
                const newMap = new Map(prev);
                let changed = false;
                artists.forEach(a => {
                  if (!newMap.has(a.id)) {
                    newMap.set(a.id, a);
                    changed = true;
                  }
                });
                return changed ? newMap : prev;
              });
            }, [recordArtistIds])}
            recordArtistIds={recordArtistIds}
            artistsMap={allEncounteredArtists}
          />

          <SongFilePicker
            key={`file-${filePickerKey}`}
            selectedFile={currentFile}
            // Logic: if editing and no new file selected, we might want to show "Keep existing" text in picker?
            // But picker props are simple. 
            // If currentFile is passed as undefined but we are editing, standard picker might show "No file selected".
            // We can improve this if needed, but for now passing currentFile (which is set from map on edit) should work.
            onFileSelect={(file, duration) => {
              setCurrentFile(file);
              setSong((prev) => {
                const filenameTitle = file.name.replace(/\.[^/.]+$/, "");
                const hasCustomTitle = prev.title.trim().length > 0;

                return {
                  ...prev,
                  totalDuration: duration,
                  title:
                    recordType === RecordType.SINGLE
                      ? prev.title || recordTitle.trim()
                      : hasCustomTitle
                        ? prev.title
                        : filenameTitle,
                };
              });
            }}
          />

          {song.totalDuration > 0 && (
            <div className="mb-6">
              <div className="flex items-center gap-2 text-sm">
                <Music className="w-4 h-4 text-red-500" />
                <span className="text-gray-200">
                  Duration:{" "}
                  <span
                    className={`font-semibold ${song.totalDuration > 600 ? "text-red-500" : "text-white"
                      }`}
                  >
                    {formatDuration(song.totalDuration)}
                  </span>
                </span>
              </div>
              {song.totalDuration > 600 && (
                <p className="mt-2 text-sm text-red-500">
                  Song duration cannot exceed 10 minutes
                </p>
              )}
            </div>
          )}

          <GenreSelector
            key={`genre-${genreSelectorKey}`}
            selectedGenreIds={song.genreIds}
            setSelectedGenreIds={(ids) => {
              setSong((prev) => ({
                ...prev,
                genreIds: typeof ids === "function" ? ids(prev.genreIds) : ids,
              }));
            }}
          />

          <div className="flex gap-4 mt-8">
            <button
              onClick={handleSaveSong}
              disabled={!canSave}
              className="flex-1 bg-red-500 hover:bg-red-600 disabled:bg-zinc-800 disabled:text-zinc-600 disabled:cursor-not-allowed text-white font-bold px-8 py-4 rounded-full transition-all"
            >
              {editingSongIndex !== null
                ? "Update Song"
                : hasReachedLimit
                  ? `Only ${maxSongsAllowed} song${maxSongsAllowed > 1 ? "s" : ""} allowed`
                  : "Add Song to List"
              }
            </button>

            {editingSongIndex !== null && (
              <button
                onClick={resetForm}
                className="px-8 py-4 rounded-full bg-zinc-700 hover:bg-zinc-600 text-white font-bold transition-all"
              >
                Cancel
              </button>
            )}
          </div>
        </div>

        {songs.length > 0 && (
          <div className="mt-8 bg-gray-800/80 rounded-2xl p-6 backdrop-blur-sm border border-gray-700">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-xl font-bold">Added Songs</h3>
              <span className="bg-red-500 text-white text-sm font-bold px-3 py-1 rounded-full">
                {songs.length} {songs.length === 1 ? "song" : "songs"}
              </span>
            </div>

            <div className="space-y-2">
              {songs.map((s, idx) => {
                const isCurrentRowPlaying =
                  !!currentSong &&
                  !!(currentSong as any).file &&
                  currentSong.title === s.title &&
                  isPlaying;

                return (
                  <div
                    key={`song-${idx}`}
                    className={`flex items-center gap-4 p-4 rounded-lg transition-colors group ${editingSongIndex === idx
                      ? "bg-red-500/10 border border-red-500 shadow-[0_0_15px_rgba(239,68,68,0.2)]"
                      : "bg-zinc-900 hover:bg-zinc-800"
                      }`}
                  >
                  <div className="flex-shrink-0 w-12 h-12 bg-zinc-800 rounded-lg flex items-center justify-center">
                    {songFiles.has(s.title.trim()) ? (
                      <button
                        onClick={() => {
                          if (isCurrentRowPlaying) {
                            togglePlay();
                          } else {
                            playFromAddSection(idx);
                          }
                        }}
                        className="w-full h-full flex items-center justify-center hover:bg-zinc-700 rounded-lg transition-colors"
                        title={isCurrentRowPlaying ? "Pause" : "Play"}
                      >
                        {isCurrentRowPlaying ? (
                          <Pause className="w-6 h-6 text-red-500" />
                        ) : (
                          <Play className="w-6 h-6 text-red-500 ml-0.5" />
                        )}
                      </button>
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-zinc-500">
                        <Music className="w-6 h-6" />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className={`font-semibold truncate ${isCurrentRowPlaying ? "text-red-500" : "text-white"}`}>
                      {s.title}
                    </div>
                    <div className="text-sm text-gray-300">
                      {getArtistNames(s.artistIds)}
                    </div>
                    <div className="text-xs text-gray-400 mt-0.5">
                      {getGenreNames(s.genreIds)} • {formatDuration(s.totalDuration)}
                    </div>
                  </div>
                  <div className="flex gap-1">
                    <button
                      onClick={() => moveSongUp(idx)}
                      disabled={idx === 0}
                      className={`p-2 rounded-lg transition-all opacity-0 group-hover:opacity-100 ${idx === 0
                        ? "cursor-not-allowed text-zinc-600"
                        : "hover:bg-zinc-700 text-zinc-400 hover:text-white"
                        }`}
                      title="Move up"
                    >
                      <ArrowUp className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => moveSongDown(idx)}
                      disabled={idx === songs.length - 1}
                      className={`p-2 rounded-lg transition-all opacity-0 group-hover:opacity-100 ${idx === songs.length - 1
                        ? "cursor-not-allowed text-zinc-600"
                        : "hover:bg-zinc-700 text-zinc-400 hover:text-white"
                        }`}
                      title="Move down"
                    >
                      <ArrowDown className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => startEditingSong(idx)}
                      disabled={editingSongIndex === idx}
                      className={`p-2 rounded-lg transition-all ${editingSongIndex === idx
                        ? "opacity-100 cursor-not-allowed text-red-500"
                        : "opacity-0 group-hover:opacity-100 hover:bg-zinc-700 text-zinc-400 hover:text-blue-500"
                        }`}
                      title="Edit song"
                    >
                      <Edit2 className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => {
                        toast(
                          `Are you sure you want to delete "${s.title}"?`,
                          {
                            action: {
                              label: "Delete",
                              onClick: () => removeSong(idx),
                            },
                            cancel: {
                              label: "Cancel",
                              onClick: () => {},
                            },
                          }
                        );
                      }}
                      className="opacity-0 group-hover:opacity-100 p-2 hover:bg-zinc-700 rounded-lg transition-all"
                      title="Remove song"
                    >
                      <Trash2 className="w-5 h-5 text-zinc-400 hover:text-red-500" />
                    </button>
                  </div>
                </div>
              );})}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AddSongSection;
