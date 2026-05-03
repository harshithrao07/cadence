"use client";

import { useArtists } from "@/context/ArtistContext";
import { ArtistPreviewDTO } from "@/types/Artists";
import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";

export const ArtistSelector = ({
  selectedArtistIds,
  setSelectedArtistIds,
  recordArtistIds = [],
  initialArtists,
  artistsMap,
  onArtistsChange,
}: {
  selectedArtistIds: string[];
  setSelectedArtistIds: (ids: string[]) => void;
  recordArtistIds: string[];
  initialArtists?: ArtistPreviewDTO[];
  artistsMap?: Map<string, ArtistPreviewDTO>;
  onArtistsChange?: (artists: ArtistPreviewDTO[]) => void;
}) => {
  const { searchArtists } = useArtists();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ArtistPreviewDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedArtists, setSelectedArtists] = useState<ArtistPreviewDTO[]>(
    initialArtists || []
  );
  const [showDropdown, setShowDropdown] = useState(false);

  // Initialize selectedArtists from IDs and Map if needed
  useEffect(() => {
    if (artistsMap || initialArtists) {
      const initial = initialArtists || [];
      const fromMap = selectedArtistIds
        .map(id => artistsMap?.get(id))
        .filter((a): a is ArtistPreviewDTO => a !== undefined);

      const combined = [...initial, ...fromMap];
      // Filter out record artists from the "Featured Artists" list
      const filtered = combined.filter(a => !recordArtistIds.includes(a.id));
      // De-duplicate
      const unique = Array.from(new Map(filtered.map(a => [a.id, a])).values());

      // Only update if the IDs have actually changed to prevent infinite loops
      const currentIds = selectedArtists.map(a => a.id).sort().join(",");
      const newIds = unique.map(a => a.id).sort().join(",");

      if (currentIds !== newIds) {
        setSelectedArtists(unique);
      }
    }
  }, [selectedArtistIds, artistsMap, initialArtists, recordArtistIds, selectedArtists]);

  useEffect(() => {
    const timer = setTimeout(async () => {
      if (!query.trim()) {
        setResults([]);
        return;
      }

      setLoading(true);
      const data = await searchArtists(query);
      setResults(data);
      setLoading(false);
      setShowDropdown(true);
    }, 300);

    return () => clearTimeout(timer);
  }, [query]);

  // Notify parent when selected artists change
  useEffect(() => {
    onArtistsChange?.(selectedArtists);
  }, [selectedArtists, onArtistsChange]);

  const toggleArtist = (artist) => {
    const isRecordArtist = recordArtistIds.some((a) => a === artist.id);

    if (isRecordArtist) return;

    setSelectedArtistIds(
      selectedArtistIds.includes(artist.id)
        ? selectedArtistIds.filter((id) => id !== artist.id)
        : [...selectedArtistIds, artist.id]
    );

    setSelectedArtists((prev) =>
      prev.some((a) => a.id === artist.id)
        ? prev.filter((a) => a.id !== artist.id)
        : [...prev, artist]
    );

    setQuery("");
    setShowDropdown(false);
  };

  return (
    <div className="mb-6">
      <label className="block text-sm font-semibold mb-3 text-white">
        Featured Artists
      </label>

      {selectedArtists.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-4">
          {selectedArtists.map((artist) => (
            <span
              key={artist.id}
              className={`flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold bg-red-500 hover:bg-red-600 text-white`}
            >
              {artist.name}
              <button onClick={() => toggleArtist(artist)}>
                <X size={14} />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="relative">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-black" />
          <input
            value={query}
            autoComplete="off"
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => query && setShowDropdown(true)}
            placeholder="Search or add featured artists..."
            className="w-full bg-zinc-900 text-black px-10 py-3 rounded-lg border border-zinc-800 focus:border-red-500 focus:outline-none transition-colors"
          />
        </div>

        {showDropdown && (query || results.length > 0) && (
          <div className="absolute z-10 w-full mt-2 bg-white border border-zinc-800 rounded-lg shadow-xl max-h-60 overflow-y-auto">
            {loading && (
              <div className="px-4 py-3 text-sm text-zinc-400">
                Searching...
              </div>
            )}

            {results.map((artist) => (
              <button
                key={artist.id}
                onClick={() => toggleArtist(artist)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-zinc-800 transition-colors text-left"
              >
                <div className="text-black font-medium">{artist.name}</div>
                {(selectedArtistIds.includes(artist.id) ||
                  recordArtistIds.includes(artist.id)) && (
                    <span className="text-red-500 text-xs font-semibold">
                      ✓ Selected
                    </span>
                  )}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
