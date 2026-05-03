"use client";

import React, { createContext, useContext, useState } from "react";
import api from "../lib/api";
import { ApiResponseDTO } from "@/types/ApiResponse";
import { RecordPreviewDTO } from "@/types/Record";

type RecordContextType = {
  records: RecordPreviewDTO[];
  loading: boolean;
  fetchRecordsByArtistId: (
    artistId: string,
    forceRefresh?: boolean
  ) => Promise<RecordPreviewDTO[] | null>;
  getRecordById: (recordId: string) => Promise<RecordPreviewDTO | null>;
};

const RecordContext = createContext<RecordContextType | undefined>(undefined);

export const useRecords = () => {
  const context = useContext(RecordContext);
  if (!context) {
    throw new Error("useRecords must be used within a RecordProvider");
  }
  return context;
};

export const RecordProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [records, setRecords] = useState<RecordPreviewDTO[]>([]);
  const [recordsByArtistId, setRecordsByArtistId] = useState<
    Record<string, RecordPreviewDTO[]>
  >({});
  const [loading, setLoading] = useState(false);
  const recordsByArtistIdRef = React.useRef(recordsByArtistId);

  React.useEffect(() => {
    recordsByArtistIdRef.current = recordsByArtistId;
  }, [recordsByArtistId]);

  const fetchRecordsByArtistId = React.useCallback(async (
    artistId: string,
    forceRefresh = false
  ): Promise<RecordPreviewDTO[] | null> => {
    // ✅ Cache hit
    if (!forceRefresh && recordsByArtistIdRef.current[artistId]) {
      setRecords(recordsByArtistIdRef.current[artistId]);
      return recordsByArtistIdRef.current[artistId];
    }

    setLoading(true);
    try {
      const res = await api.get<ApiResponseDTO<RecordPreviewDTO[]>>(
        "/api/v1/record/all",
        { params: { artistId } }
      );

      if (res.data.success) {
        const data = res.data.data;

        setRecords(data);
        setRecordsByArtistId((prev) => ({
          ...prev,
          [artistId]: data,
        }));

        return data;
      }
    } catch (err) {
      console.error("Failed to fetch records:", err);
    } finally {
      setLoading(false);
    }

    return null;
  }, []);

  const getRecordById = React.useCallback(async (
    recordId: string
  ): Promise<RecordPreviewDTO | null> => {
    // 🔍 Optional: try cache first
    // Note: The cache stores RecordPreviewDTO.

    try {
      const res = await api.get<ApiResponseDTO<any>>(
        `/api/v1/record/${recordId}`
      );

      if (res.data.success) {
        const data = res.data.data;
        // Map recordArtists to artists if present
        if (data.recordArtists && !data.artists) {
          data.artists = data.recordArtists;
        }
        return data as RecordPreviewDTO;
      }
    } catch (err) {
      console.error("Failed to fetch record:", err);
    }

    return null;
  }, []);

  return (
    <RecordContext.Provider
      value={{
        records,
        loading,
        fetchRecordsByArtistId,
        getRecordById,
      }}
    >
      {children}
    </RecordContext.Provider>
  );
};
