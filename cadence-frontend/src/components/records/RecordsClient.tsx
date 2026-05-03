"use client";

import React, { useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Image from "next/image";
import { useRecords } from "../../context/RecordContext";
import { useSongs } from "../../context/SongContext";
import { RecordPreviewDTO, RecordType } from "../../types/Record";
import { EachSongDTO } from "../../types/Song";
import { Clock, ArrowLeft } from "lucide-react";

const formatDate = (timestamp: number) => {
  return new Date(timestamp).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
};

interface RecordWithSongs {
  record: RecordPreviewDTO;
  songs: EachSongDTO[];
}

export default function RecordsClient() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { fetchRecordsByArtistId } = useRecords();
  const { fetchSongsByRecordId } = useSongs();

  const [recordsWithSongs, setRecordsWithSongs] = useState<RecordWithSongs[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const artistId = searchParams.get("artistId");

  useEffect(() => {
    const fetchData = async () => {
      if (!artistId) {
        setError("No artist specified");
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError(null);

        // Fetch records for this artist
        const records = await fetchRecordsByArtistId(artistId);
        if (!records) {
          setRecordsWithSongs([]);
          setLoading(false);
          return;
        }

        // Fetch songs for each record
        const recordsWithSongsPromises = records.map(async (record) => {
          const songs = await fetchSongsByRecordId(record.id);
          return {
            record,
            songs: songs || [],
          };
        });

        const recordsWithSongsData = await Promise.all(recordsWithSongsPromises);

        // Sort by chronological order (newest first)
        recordsWithSongsData.sort((a, b) =>
          b.record.releaseTimestamp - a.record.releaseTimestamp
        );

        setRecordsWithSongs(recordsWithSongsData);
      } catch (err) {
        console.error("Failed to fetch records:", err);
        setError("Failed to load records");
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [artistId, fetchRecordsByArtistId, fetchSongsByRecordId]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-red-900 via-black to-black text-white flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-red-500 mx-auto mb-4"></div>
          <p>Loading records...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-red-900 via-black to-black text-white flex items-center justify-center">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-red-500 mb-4">Error</h1>
          <p className="text-gray-400 mb-6">{error}</p>
          <button
            onClick={() => router.back()}
            className="bg-red-500 hover:bg-red-600 px-6 py-3 rounded-full font-bold transition"
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen text-white">
      {/* Content */}
      <div className="max-w-6xl mx-auto px-6 py-8">
        {recordsWithSongs.length === 0 ? (
          <div className="text-center py-12">
            <div className="text-gray-400 text-lg mb-2">No records found</div>
            <div className="text-gray-500 text-sm">This artist has not released any records yet.</div>
          </div>
        ) : (
          <div className="space-y-8">
            {recordsWithSongs.map(({ record, songs }) => {
              const totalDuration = songs.reduce(
                (acc, song) => acc + song.totalDuration,
                0
              );
              const totalMinutes = Math.floor(totalDuration / 60);

              return (
                <div
                  key={record.id}
                  onClick={() => router.push(`/records/${record.id}`)}
                  className="bg-gray-900/50 rounded-xl p-6 backdrop-blur-sm border border-gray-800 hover:border-gray-700 transition cursor-pointer group"
                >
                  <div className="flex flex-col md:flex-row gap-6">
                    {/* Cover */}
                    <div className="flex-shrink-0">
                      <Image
                        src={record.coverUrl || "/images/records/record-placeholder.png"}
                        alt={record.title}
                        className="w-32 h-32 md:w-40 md:h-40 rounded-lg shadow-lg group-hover:scale-105 transition"
                        width={160}
                        height={160}
                      />
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start justify-between mb-2">
                        <div>
                          <p className="text-sm font-medium uppercase tracking-wider text-gray-400 mb-1">
                            {record.recordType}
                          </p>
                          <h2 className="text-2xl md:text-3xl font-bold mb-2 group-hover:text-red-400 transition">
                            {record.title}
                          </h2>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 text-sm text-gray-400 mb-4">
                        <span>{formatDate(record.releaseTimestamp)}</span>
                        <span>•</span>
                        <span>{songs.length} songs</span>
                        {totalMinutes > 0 && (
                          <>
                            <span>•</span>
                            <span className="flex items-center gap-1">
                              <Clock className="w-4 h-4" />
                              {totalMinutes} min
                            </span>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
