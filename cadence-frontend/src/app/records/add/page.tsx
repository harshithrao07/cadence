"use client";

import React, { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Music, Disc, Album, Trash2, ArrowLeft, X } from "lucide-react";
import axios from "axios";
import {
  RecordType,
  RECORD_LIMITS,
  UpsertRecordDTO,
  UpsertRecordResponseDTO,
} from "@/types/Record";
import { useArtists } from "@/context/ArtistContext";
import { useUser } from "@/context/UserContext";
import { usePlayer } from "@/context/PlayerContext";
import Image from "next/image";
import api from "@/lib/api";
import AddSongSection, { EditableSong } from "@/components/songs/AddSongSection";
import { EachSongDTO, UpsertSongDTO } from "@/types/Song";
import { ArtistPreviewDTO } from "@/types/Artists";
import { RecordPreviewDTO } from "@/types/Record";
import { toast } from "sonner";
import { FileUploadResult, MetadataDTO, HTTPMethod } from "@/types/Aws";

interface AddRecordPageProps {
  isUpdate?: boolean;
  existingRecord?: RecordPreviewDTO;
  existingSongs?: EachSongDTO[];
  onUpdateSuccess?: () => void;
}

export default function AddRecordPage({ isUpdate = false, existingRecord, existingSongs = [], onUpdateSuccess }: AddRecordPageProps) {
  const router = useRouter();
  const { searchArtists } = useArtists();
  const { isAdmin } = useUser();
  const { resetPlayer } = usePlayer();

  const [selectedType, setSelectedType] = useState<RecordType>(existingRecord?.recordType || null);
  const [coverPreview, setCoverPreview] = useState<string | null>(existingRecord?.coverUrl || null);
  const fileInputRef = useRef<File | null>(null);
  const fileInputDomRef = useRef<HTMLInputElement>(null);
  const [newRecord, setNewRecord] = useState<{
    title: string;
    releaseTimestamp: string;
    recordType: RecordType | null;
    artistIds: string[];
  }>({
    title: existingRecord?.title || "",
    releaseTimestamp: existingRecord ? new Date(existingRecord.releaseTimestamp).toISOString().split('T')[0] : "",
    recordType: existingRecord?.recordType || null,
    artistIds: isUpdate && existingRecord?.recordArtists ? existingRecord.recordArtists.map(a => a.id) : [],
  });
  const [songs, setSongs] = useState<EditableSong[]>(() => {
    if (isUpdate && existingSongs) {
      return existingSongs.map((song, index) => ({
        id: song.id,
        title: song.title,
        genreIds: song.genres?.map(g => g.id) || [], // Extract genre IDs from existing genres
        artistIds: song.artists?.map(a => a.id) || [],
        totalDuration: song.totalDuration,
        order: index,
        coverUrl: "",
      }));
    }
    return [];
  });
  const [songFiles, setSongFiles] = useState<Map<string, File>>(new Map());
  const [artistSearch, setArtistSearch] = useState("");
  const [artistResults, setArtistResults] = useState<ArtistPreviewDTO[]>([]);
  const [artistLoading, setArtistLoading] = useState(false);
  const [selectedArtists, setSelectedArtists] = useState<ArtistPreviewDTO[]>(
    isUpdate && existingRecord?.recordArtists ? existingRecord.recordArtists : []
  );
  const [isSubmitting, setIsSubmitting] = useState(false);

  const uploadSongFilesInBatches = async (entries: { file: File; filename: string }[]) => {
    if (!entries.length) return;

    for (const { file, filename } of entries) {
      const formData = new FormData();
      formData.append("file", file, filename);

      await api.post("/api/v1/files", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
    }
  };

  React.useEffect(() => {
    if (isUpdate) {
      resetPlayer();
    }
    return () => {
      resetPlayer();
    };
  }, [isUpdate, resetPlayer]);

  const [deletedSongIds, setDeletedSongIds] = useState<string[]>([]);

  const handleSongsChange = (updatedSongs: EditableSong[], deletedIds?: string[]) => {
    setSongs(updatedSongs);
    if (deletedIds) {
      setDeletedSongIds(deletedIds);
    }
  };

  const handleSongFilesChange = (updatedSongFiles: Map<string, File>) => {
    setSongFiles(updatedSongFiles);
  };

  const handleSongUpdate = async (updatedSong: EditableSong) => {
    // TODO: Implement individual song update API call
    // For now, just return a resolved promise
  };

  const recordTypes = [
    { type: RecordType.SINGLE, label: "Single", icon: Music },
    { type: RecordType.EP, label: "EP", icon: Disc },
    { type: RecordType.ALBUM, label: "Album", icon: Album },
  ];

  const recordTypeMap = {
    [RecordType.SINGLE]: { label: "Single", icon: Music },
    [RecordType.EP]: { label: "EP", icon: Disc },
    [RecordType.ALBUM]: { label: "Album", icon: Album },
  };

  React.useEffect(() => {
    const timer = setTimeout(async () => {
      if (!artistSearch.trim()) {
        setArtistResults([]);
        return;
      }

      setArtistLoading(true);
      try {
        const data = await searchArtists(artistSearch.trim());
        setArtistResults(data || []);
      } finally {
        setArtistLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [artistSearch]);

  const handleBack = () => {
    // If in update mode, close the modal instead of navigating back
    if (isUpdate) {
      if (onUpdateSuccess) {
        onUpdateSuccess(); // This will close the modal
      }
      return;
    }

    // If no record type selected, behave like normal back
    if (!selectedType) {
      router.back();
      return;
    }

    // Ask only if something was entered
    if (!hasUnsavedChanges()) {
      setSelectedType(null);
      return;
    }

    if (window.confirm("Going back will clear your entered data. Continue?")) {
      setSelectedType(null);
      setCoverPreview(null);
      setNewRecord({
        title: "",
        releaseTimestamp: "",
        recordType: null,
        artistIds: [],
      });
    }
  };

  const hasUnsavedChanges = () => {
    return (
      newRecord.title.trim() !== "" ||
      newRecord.releaseTimestamp !== "" ||
      newRecord.artistIds.length > 0 ||
      coverPreview !== null
    );
  };

  const handleSubmit = async () => {
    if (songs.length === 0) {
      toast.error("A record must have at least one song.");
      return;
    }

    if (songs.length > RECORD_LIMITS[selectedType]) {
      toast.error(
        `You can only add up to ${RECORD_LIMITS[selectedType]
        } songs to a ${selectedType.toLowerCase()}.`
      );
      return;
    }

    const recordDTO: UpsertRecordDTO = {
      id: isUpdate && existingRecord ? existingRecord.id : undefined,
      title: newRecord.title,
      releaseTimestamp: newRecord.releaseTimestamp ? new Date(newRecord.releaseTimestamp).getTime() : Date.now(),
      recordType: selectedType,
      artistIds: newRecord.artistIds,
      songs: songs.map((song) => ({
        id: song.id,
        title: song.title,
        genreIds: song.genreIds,
        artistIds: song.artistIds,
        totalDuration: song.totalDuration,
      })),
    };

    try {
      setIsSubmitting(true);
      
      const res = await api.post("/api/v1/record/upsert", recordDTO);

      if (res.status >= 200 && res.status < 300) {
        const savedRecord = res.data.data as UpsertRecordResponseDTO;
        const recordId = savedRecord.id;

        if (fileInputRef.current) {
          const file = fileInputRef.current;

          const formData = new FormData();
          formData.append(
            "file",
            file,
            `record cover_url ${recordId}`
          );

          await api.post<FileUploadResult[]>("/api/v1/files", formData, {
            headers: { "Content-Type": "multipart/form-data" },
          });
        }

        const savedSongs = savedRecord.songs;
        if (savedSongs && Array.isArray(savedSongs)) {
          const uploadPromises: Promise<void>[] = [];

          for (const song of savedSongs) {
            const title = song.title.trim();
            const file = songFiles.get(title);
            if (!file || !song.presignedUrl) continue;

            const uploadPromise = axios.put(song.presignedUrl, file, {
              headers: {
                "Content-Type": file.type || "application/octet-stream",
              },
            }).then(() => {
              // Upload successful
            }).catch(err => {
              console.error(`Failed to upload song ${title}:`, err);
              toast.error(`Failed to upload song: ${title}`);
            });

            uploadPromises.push(uploadPromise);
          }

          if (uploadPromises.length > 0) {
            await Promise.all(uploadPromises);
          }
        }

        // Handle song deletions
        if (deletedSongIds.length > 0) {
          const deletePromises = deletedSongIds.map(async (songId) => {
            try {
              const metadata: MetadataDTO = {
                category: "song",
                subCategory: "song_url",
                primaryKey: songId,
                httpMethod: HTTPMethod.DELETE
              };
              
              const res = await api.post<string>("/api/v1/files/presigned-url", metadata);
              if (res.status === 200 && res.data) {
                const presignedUrl = res.data;
                await axios.delete(presignedUrl);
              }
            } catch (err) {
              console.error(`Failed to delete song ${songId}:`, err);
              // Don't block success on deletion failure
            }
          });
          
          await Promise.all(deletePromises);
        }

        // Handle record cover deletion
        if (isUpdate && existingRecord?.coverUrl && !coverPreview && !fileInputRef.current) {
          try {
            const metadata: MetadataDTO = {
              category: "record",
              subCategory: "cover_url",
              primaryKey: recordId,
              httpMethod: HTTPMethod.DELETE
            };
            
            const res = await api.post<string>("/api/v1/files/presigned-url", metadata);
            if (res.status === 200 && res.data) {
              const presignedUrl = res.data;
              await axios.delete(presignedUrl);
            }
          } catch (err) {
            console.error("Failed to delete record cover:", err);
          }
        }

        toast.success(isUpdate ? "Record updated successfully" : "Record created successfully");

        const hasNewFile = !!fileInputRef.current;
        const hasDeletedFile = isUpdate && existingRecord?.coverUrl && !coverPreview && !fileInputRef.current;
        
        if (hasNewFile || hasDeletedFile) {
          toast.info("Changes may take up to 5 minutes to reflect due to caching");
        }
        
        if (isUpdate && onUpdateSuccess) {
          onUpdateSuccess();
        } else {
          router.push(`/records/${recordId}`);
        }
      }
    } catch (error) {
      console.error("Error saving record:", error);
      toast.error("Failed to save record");
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleArtist = (artist: any) => {
    setNewRecord((prev) => ({
      ...prev,
      artistIds: prev.artistIds.includes(artist.id)
        ? prev.artistIds.filter((id) => id !== artist.id)
        : [...prev.artistIds, artist.id],
    }));

    setSelectedArtists((prev) =>
      prev.some((a) => a.id === artist.id)
        ? prev.filter((a) => a.id !== artist.id)
        : [...prev, artist]
    );

    setArtistSearch("");
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    fileInputRef.current = file;

    const reader = new FileReader();
    reader.onloadend = () => {
      setCoverPreview(reader.result as string);
    };
    reader.readAsDataURL(file);
  };

  const removeCover = () => {
    setCoverPreview(null);
    fileInputRef.current = null;
    if (fileInputDomRef.current) {
      fileInputDomRef.current.value = "";
    }
    setNewRecord({
      ...newRecord,
    });
  };

  // Check admin access
  if (isAdmin === false) {
    return (
      <div className="min-h-screen bg-black text-white flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-red-500 mb-4">Access Denied</h1>
          <p className="text-gray-400">You need admin privileges to access this page.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-black text-white">
      {/* Header */}
      <div className="sticky top-0 z-20 bg-gray-900 border-b border-gray-800">
        <div className="max-w-5xl mx-auto px-6 py-4 flex items-center gap-4">
          <button
            onClick={handleBack}
            className="text-gray-400 hover:text-white transition"
          >
            <ArrowLeft />
          </button>
          <h1 className="text-2xl font-bold">{isUpdate ? "Update Record" : "Add New Record"}</h1>
        </div>
      </div>

      {selectedType && (
        <div className="max-w-5xl mx-auto px-6 pt-6">
          <div className="flex items-center gap-3 bg-gray-900 border border-gray-800 rounded-xl px-4 py-3">
            {(() => {
              const Icon = recordTypeMap[selectedType].icon;
              return <Icon className="w-5 h-5 text-red-500" />;
            })()}

            <span className="text-sm text-gray-400">Creating</span>
            <span className="font-semibold text-white">
              {recordTypeMap[selectedType].label}
            </span>
          </div>
        </div>
      )}

      {/* Content */}
      <div className="max-w-5xl mx-auto px-6 py-8">
        {!selectedType ? (
          <div>
            <h2 className="text-lg font-semibold mb-6">Select Record Type</h2>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
              {recordTypes.map(({ type, label, icon: Icon }) => (
                <button
                  key={type}
                  onClick={() => setSelectedType(type)}
                  className="p-8 bg-gray-800 hover:bg-gray-700 rounded-xl flex flex-col items-center transition"
                >
                  <Icon className="w-12 h-12 text-red-500 mb-4" />
                  <span className="font-semibold">{label}</span>
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="space-y-8">
            {/* Title */}
            <div>
              <label className="block mb-2 text-sm text-gray-300">Title</label>
              <input
                type="text"
                value={newRecord.title}
                onChange={(e) =>
                  setNewRecord({ ...newRecord, title: e.target.value })
                }
                className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg focus:border-red-500 outline-none"
              />
            </div>

            {/* Release date */}
            <div>
              <label className="block mb-2 text-sm text-gray-300">
                Release Date
              </label>
              <input
                type="date"
                value={newRecord.releaseTimestamp}
                onChange={(e) =>
                  setNewRecord({
                    ...newRecord,
                    releaseTimestamp: e.target.value,
                  })
                }
                className="w-full px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-white"
                style={{ colorScheme: "dark" }}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Cover Image
              </label>

              {/* File upload only */}
              <input
                ref={fileInputDomRef}
                type="file"
                accept="image/*"
                onChange={handleFileUpload}
                className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg
      text-white focus:outline-none focus:border-red-500 transition
      file:mr-4 file:py-2 file:px-4 file:rounded
      file:border-0 file:bg-red-500 file:text-white
      file:cursor-pointer hover:file:bg-red-600"
              />

              {coverPreview && (
                <div className="mt-3 relative inline-block">
                  <Image
                    src={coverPreview}
                    alt="Cover preview"
                    className="w-40 h-40 object-cover rounded-lg border-2 border-gray-700"
                    width={160}
                    height={160}
                  />
                  <button
                    onClick={removeCover}
                    className="absolute -top-2 -right-2 bg-red-500 rounded-full p-1.5 hover:bg-red-600 transition"
                  >
                    <Trash2 className="w-4 h-4 text-white" />
                  </button>
                </div>
              )}
            </div>

            {/* Artists */}
            <div>
              <label className="block mb-2 text-sm text-gray-300">
                Artists
              </label>

              {/* Search input */}
              <div className="relative">
                <input
                  placeholder="Search artist..."
                  value={artistSearch}
                  autoComplete="off"
                  onChange={(e) => setArtistSearch(e.target.value)}
                  className="w-full px-4 py-3 pr-10 bg-gray-800 border border-gray-700 rounded-lg outline-none focus:border-red-500"
                />

                {artistSearch && (
                  <button
                    type="button"
                    onClick={() => {
                      setArtistSearch("");
                      setArtistResults([]);
                    }}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition"
                    aria-label="Clear search"
                  >
                    <X className="w-5 h-5" />
                  </button>
                )}
              </div>

              {/* Results */}
              <div className="mt-3 space-y-1 max-h-64 overflow-y-auto">
                {artistLoading && (
                  <div className="px-4 py-3 text-sm text-gray-400">
                    Searching...
                  </div>
                )}

                {!artistLoading &&
                  artistSearch &&
                  artistResults.length === 0 && (
                    <div className="px-4 py-3 text-sm text-gray-400">
                      No artists found
                    </div>
                  )}

                {artistResults.map((artist) => {
                  const isSelected = newRecord.artistIds.includes(artist.id);

                  return (
                    <button
                      key={artist.id}
                      onClick={() => toggleArtist(artist)}
                      className={`
            w-full px-4 py-3 flex items-center gap-3 rounded-lg transition-all duration-200
            ${isSelected
                          ? "bg-gray-700 border border-red-500 shadow-[0_0_0_1px_rgba(239,68,68,0.6)]"
                          : "hover:bg-gray-700 border border-transparent"
                        }
            active:scale-[0.98]
          `}
                    >
                      {/* Avatar */}
                      <div
                        className={`relative ${isSelected
                          ? "ring-2 ring-red-500 ring-offset-2 ring-offset-gray-800 rounded-full"
                          : ""
                          }`}
                      >
                        <Image
                          src={
                            artist.profileUrl ||
                            "/images/artists/artist-placeholder.png"
                          }
                          className="w-9 h-9 rounded-full object-cover"
                          alt={artist.name}
                          width={36}
                          height={36}
                        />
                      </div>

                      <span className="flex-1 text-left font-medium">
                        {artist.name}
                      </span>

                      <div
                        className={`
              w-6 h-6 rounded-full border flex items-center justify-center transition-all
              ${isSelected
                            ? "bg-red-500 border-red-500 scale-100 opacity-100"
                            : "border-gray-600 scale-75 opacity-0"
                          }
            `}
                      >
                        ✓
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {selectedArtists.length > 0 && (
              <div className="mt-6">
                <p className="text-sm text-gray-400 mb-2">Selected artists</p>

                <div className="flex flex-wrap gap-2">
                  {selectedArtists.map((artist) => (
                    <span
                      key={artist.id}
                      className="flex items-center gap-2 bg-red-500/10 border border-red-500/40 text-white px-4 py-2 rounded-full text-sm"
                    >
                      <Image
                        src={
                          artist.profileUrl ||
                          "/images/artists/artist-placeholder.png"
                        }
                        alt={artist.name}
                        className="w-5 h-5 rounded-full"
                        width={20}
                        height={20}
                      />
                      {artist.name}
                    </span>
                  ))}
                </div>
              </div>
            )}

            <AddSongSection
              selectedArtists={selectedArtists}
              recordType={selectedType}
              recordTitle={newRecord.title}
              onSongsChange={handleSongsChange}
              onSongFilesChange={handleSongFilesChange}
              initialSongs={songs}
              isUpdate={isUpdate}
              fullExistingSongs={existingSongs}
            />

            {/* Actions */}
            <div className="flex gap-4 pt-6">
              <button
                onClick={handleBack}
                disabled={isSubmitting}
                className={`flex-1 bg-gray-800 py-3 rounded-lg ${isSubmitting ? "opacity-60 cursor-not-allowed" : ""}`}
              >
                Back
              </button>
              <button
                onClick={handleSubmit}
                disabled={isSubmitting}
                className={`flex-1 py-3 rounded-lg font-semibold flex items-center justify-center gap-2 ${isSubmitting
                  ? "bg-red-500/60 cursor-not-allowed"
                  : "bg-red-500 hover:bg-red-600"
                  }`}
              >
                {isSubmitting && (
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                )}
                <span>
                  {isSubmitting
                    ? isUpdate
                      ? "Updating..."
                      : "Creating..."
                    : isUpdate
                      ? "Update Record"
                      : "Create Record"}
                </span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
