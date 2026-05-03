"use client";

import React, { createContext, useContext, useState } from "react";
import api from "../lib/api";
import { ApiResponseDTO } from "@/types/ApiResponse";
import { EachSongDTO } from "@/types/Song";

type SongContextType = {
  songsByRecordId: Record<string, EachSongDTO[]>;
  loading: boolean;
  fetchSongsByRecordId: (recordId: string, forceRefresh?: boolean) => Promise<EachSongDTO[] | null>;
};
  
const SongContext = createContext<SongContextType | undefined>(undefined);

export const useSongs = () => {
  const context = useContext(SongContext);
  if (!context) {
    throw new Error("useSongs must be used within a SongProvider");
  }
  return context;
};

export const SongProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [songsByRecordId, setSongsByRecordId] = useState<Record<string, EachSongDTO[]>>({});
  const [loading, setLoading] = useState(false);
  const songsByRecordIdRef = React.useRef(songsByRecordId);

  React.useEffect(() => {
    songsByRecordIdRef.current = songsByRecordId;
  }, [songsByRecordId]);

  const fetchSongsByRecordId = React.useCallback(async (
    recordId: string,
    forceRefresh = false
  ): Promise<EachSongDTO[] | null> => {
    // ✅ Cache hit
    if (!forceRefresh && songsByRecordIdRef.current[recordId]) {
      return songsByRecordIdRef.current[recordId];
    }

    setLoading(true);
    try {
      const res = await api.get<ApiResponseDTO<EachSongDTO[]>>(
        `/api/v1/song/all`,
        {
          params: { recordId }
        }
      );

      if (res.data.success) {
        const data = res.data.data;

        setSongsByRecordId((prev) => ({
          ...prev,
          [recordId]: data,
        }));

        return data;
      }
    } catch (err) {
      console.error("Failed to fetch songs:", err);
    } finally {
      setLoading(false);
    }

    return null;
  }, []);

  return (
    <SongContext.Provider
      value={{
        songsByRecordId,
        loading,
        fetchSongsByRecordId,
      }}
    >
      {children}
    </SongContext.Provider>
  );
};
