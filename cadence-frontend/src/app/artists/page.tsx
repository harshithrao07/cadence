"use client";

import React, { useState, useEffect } from "react";
import { User, Plus, X, Search, Disc } from "lucide-react";
import Image from "next/image";
import AddArtist from "../../components/artists/AddArtist";
import { useArtists } from "../../context/ArtistContext";
import { useUser } from "../../context/UserContext";
import Link from "next/link";
import { useRouter } from "next/navigation";

export default function ArtistsPage() {
  const { artists, loading, hasMore, loadMore, refreshArtists, searchArtists } =
    useArtists();
  const { isAdmin, checkIsAdmin } = useUser();

  const [showAddForm, setShowAddForm] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const router = useRouter();

  useEffect(() => {
    checkIsAdmin();
  }, []);

  /* 🔍 Debounced backend search */
  useEffect(() => {
    const timer = setTimeout(() => {
      searchArtists(searchQuery);
    }, 400);

    return () => clearTimeout(timer);
  }, [searchQuery]);

  return (
    <div className="min-h-screen text-white">
      <header className="relative px-8 pt-16 pb-24">
        <div className="absolute inset-0 bg-gradient-to-br from-red-500/10 to-transparent pointer-events-none" />
        <div className="relative">
          <h2 className="text-5xl font-black my-2 bg-gradient-to-r from-white to-zinc-400 bg-clip-text text-transparent">
            All Artists
          </h2>

          <p className="text-zinc-400 text-lg">
            Showing {artists.length} artists
          </p>
        </div>
      </header>

      <div className="px-8 -mt-12 pb-12 relative z-20">
        {showAddForm ? (
          <AddArtist setShowAddForm={setShowAddForm} />
        ) : (
          <>
            {/* Controls */}
            <div className="mb-8 flex flex-col sm:flex-row sm:items-center gap-4">
              {/* Search (Left) */}
              <div className="relative w-full sm:max-w-md flex-1">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search artists..."
                  className="w-full text-black bg-zinc-900/80 border border-zinc-800 rounded-full pl-12 pr-12 py-4 focus:outline-none focus:border-red-500"
                />

                <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-black" />

                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery("")}
                    className="absolute right-12 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-white"
                  >
                    <X className="w-5 h-5" />
                  </button>
                )}

                <button
                  onClick={refreshArtists}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-white"
                  title="Refresh"
                >
                  <User className="w-5 h-5 rotate-180 animate-spin-slow" />
                </button>
              </div>

              {/* Actions (Right) */}
              <div className="flex gap-3 flex-wrap sm:flex-nowrap flex-shrink-0">
                {isAdmin && (
                  <button
                    onClick={() => setShowAddForm(true)}
                    className="flex items-center gap-2 bg-red-500 hover:bg-red-600 px-6 py-4 rounded-full font-bold min-w-fit"
                  >
                    <Plus className="w-5 h-5" />
                    Add Artist
                  </button>
                )}

                {isAdmin && (
                  <button
                    onClick={() => router.push("/records/add")}
                    className="flex items-center gap-2 bg-zinc-800 hover:bg-zinc-700 px-6 py-4 rounded-full font-bold min-w-fit"
                  >
                    <Disc className="w-5 h-5" />
                    Add Record
                  </button>
                )}
              </div>
            </div>

            {/* Grid */}
            {loading && artists.length === 0 ? (
              <div className="flex items-center justify-center py-32">
                <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-red-500"></div>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-5">
                  {artists.map((artist) => (
                    <Link href={`/artists/${artist.id}`} key={artist.id}>
                      <div className="bg-zinc-900/40 hover:bg-zinc-800/60 p-5 rounded-xl cursor-pointer">
                        <div className="aspect-square rounded-xl mb-4 overflow-hidden relative">
                          {artist.profileUrl ? (
                            <Image
                              src={artist.profileUrl}
                              alt={artist.name}
                              fill
                              className="object-cover"
                            />
                          ) : (
                            <div className="flex items-center justify-center h-full">
                              <User className="w-16 h-16 text-zinc-700" />
                            </div>
                          )}
                        </div>
                        <h3 className="font-bold truncate">{artist.name}</h3>
                      </div>
                    </Link>
                  ))}
                </div>

                {/* Load More */}
                {hasMore && (
                  <div className="flex justify-center mt-12">
                    <button
                      onClick={loadMore}
                      disabled={loading}
                      className="px-8 py-4 rounded-full bg-zinc-800 hover:bg-zinc-700 font-bold"
                    >
                      {loading ? "Loading..." : "Load More"}
                    </button>
                  </div>
                )}
              </>
            )}
          </>
        )}
        {/* Empty State */}
        {!loading && artists.length === 0 && !showAddForm && (
          <div className="text-center py-32">
            <User className="w-16 h-16 mx-auto text-zinc-600 mb-4" />
            <p className="text-zinc-400 text-xl">No artists found</p>
            <p className="text-zinc-500 text-sm mt-2">
              {searchQuery
                ? "Try adjusting your search"
                : "Add your first artist to get started"}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
