"use client";

import React, { createContext, useContext, useState, useEffect } from "react";
import { ArtistPreviewDTO } from "../types/Artists";
import api from "../lib/api";
import { usePathname } from "next/navigation";
import { ApiResponseDTO, PaginatedResponseDTO } from "@/types/ApiResponse";

type ArtistsContextType = {
  artists: ArtistPreviewDTO[];
  loading: boolean;
  hasMore: boolean;
  loadMore: () => Promise<void>;
  refreshArtists: () => Promise<void>;
  searchArtists: (query: string) => Promise<ArtistPreviewDTO[]>;
  getArtistById: (id: string) => ArtistPreviewDTO | undefined;
};

const ArtistsContext = createContext<ArtistsContextType | undefined>(undefined);

export const useArtists = () => {
  const context = useContext(ArtistsContext);
  if (!context) {
    throw new Error("useArtists must be used within an ArtistsProvider");
  }
  return context;
};

const PAGE_SIZE = 24;

export const ArtistsProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const pathname = usePathname();

  const [artists, setArtists] = useState<ArtistPreviewDTO[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [searchKey, setSearchKey] = useState<string | null>(null);

  const fetchArtists = async (pageToLoad: number, replace = false, query?: string): Promise<ArtistPreviewDTO[]> => {
    if (pathname.startsWith("/auth")) return [];

    setLoading(true);
    try {
      const finalSearchKey = query !== undefined ? query : searchKey;

      const res = await api.get<ApiResponseDTO<PaginatedResponseDTO<ArtistPreviewDTO>>>(
        "/api/v1/artist/all",
        {
          params: {
            page: pageToLoad,
            size: PAGE_SIZE,
            key: finalSearchKey || undefined,
          },
        }
      );

      const pageData = res.data.data;

      if (query === undefined || replace) {
        // Update global artist list if this is a global state search (query undefined)
        // OR if we are explicitly replacing the list (e.g. new search)
        setArtists((prev) =>
          replace ? pageData.content : [...prev, ...pageData.content]
        );
        setHasMore(!pageData.last);
        setPage(pageData.page);
      }

      return pageData.content;
    } catch (err) {
      console.error("Failed to fetch artists:", err);
      return [];
    } finally {
      setLoading(false);
    }
  };

  const refreshArtists = async () => {
    setArtists([]);
    setPage(0);
    setHasMore(true);
    await fetchArtists(0, true);
  };

  const loadMore = async () => {
    if (loading || !hasMore) return;
    await fetchArtists(page + 1);
  };

  const searchArtists = async (query: string): Promise<ArtistPreviewDTO[]> => {
    setSearchKey(query || null);
    // When performing a global search, update the global state
    return await fetchArtists(0, true, query || "");
  };

  const getArtistById = (id: string) =>
    artists.find((artist) => artist.id === id);

  useEffect(() => {
    refreshArtists();
  }, []);

  return (
    <ArtistsContext.Provider
      value={{
        artists,
        loading,
        hasMore,
        loadMore,
        refreshArtists,
        searchArtists,
        getArtistById,
      }}
    >
      {children}
    </ArtistsContext.Provider>
  );
};
