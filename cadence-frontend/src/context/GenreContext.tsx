"use client";

import React, { createContext, useContext, useState } from "react";
import api from "../lib/api";
import { ApiResponseDTO } from "@/types/ApiResponse";
import { GenrePreviewDTO, NewGenreDTO } from "@/types/Genre";

type GenreContextType = {
  genres: GenrePreviewDTO[];
  loading: boolean;
  fetchGenres: () => Promise<void>;
  addGenre: (type: NewGenreDTO) => Promise<GenrePreviewDTO | null>;
  searchGenres: (key: string) => Promise<GenrePreviewDTO[] | null>;
};

const GenreContext = createContext<GenreContextType | undefined>(undefined);

export const useGenres = () => {
  const context = useContext(GenreContext);
  if (!context) {
    throw new Error("useGenres must be used within a GenreProvider");
  }
  return context;
};

export const GenreProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [genres, setGenres] = useState<GenrePreviewDTO[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchGenres = async () => {
    setLoading(true);
    try {
      const res = await api.get<any>(
        "/api/v1/genre/all"
      );

      // Handle different response structures
      let data: GenrePreviewDTO[] = [];
      if (res.data?.success && Array.isArray(res.data.data)) {
        data = res.data.data;
      } else if (res.data?.data?.content && Array.isArray(res.data.data.content)) {
        // Handle paginated response wrapped in ApiResponse
        data = res.data.data.content;
      } else if (Array.isArray(res.data)) {
        data = res.data;
      } else if (res.data?.content && Array.isArray(res.data.content)) {
        data = res.data.content;
      } else if (res.data?.data && Array.isArray(res.data.data)) {
        // Fallback for success=undefined but data exists
        data = res.data.data;
      }

      setGenres(data);
    } catch (err) {
      console.error("Failed to fetch genres:", err);
    } finally {
      setLoading(false);
    }
  };

  const searchGenres = async (
    key: string
  ): Promise<GenrePreviewDTO[] | null> => {
    try {
      const res = await api.get<any>(
        "/api/v1/genre/all",
        { params: { key } }
      );

      // Handle different response structures
      if (res.data?.success && Array.isArray(res.data.data)) {
        return res.data.data;
      }
      if (res.data?.data?.content && Array.isArray(res.data.data.content)) {
        return res.data.data.content;
      }
      if (Array.isArray(res.data)) {
        return res.data;
      }
      if (res.data?.content && Array.isArray(res.data.content)) {
        return res.data.content;
      }
      if (res.data?.data && Array.isArray(res.data.data)) {
        return res.data.data;
      }
      
      console.log("Genre search response format not recognized:", res.data);
      return [];
    } catch (err) {
      console.error("Failed to search genres:", err);
    }
    return null;
  };

  const addGenre = async (
    dto: NewGenreDTO
  ): Promise<GenrePreviewDTO | null> => {
    const res = await api.post<ApiResponseDTO<string>>("/api/v1/genre/add", dto);

    if (!res.data.success) return null;

    const newGenre: GenrePreviewDTO = {
      id: res.data.data,
      type: dto.type,
    };

    setGenres((prev) => {
      if (prev.some((g) => g.id === newGenre.id)) return prev;
      return [...prev, newGenre];
    });

    return newGenre;
  };

  return (
    <GenreContext.Provider
      value={{
        genres,
        loading,
        fetchGenres,
        addGenre,
        searchGenres
      }}
    >
      {children}
    </GenreContext.Provider>
  );
};
