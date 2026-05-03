"use client";

import React, { createContext, useContext, useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import api from "../lib/api";
import { toast } from "sonner";
import { EachSongDTO } from "@/types/Song";
import { ApiResponseDTO } from "@/types/ApiResponse";
import { UserProfileDTO } from "@/types/User";

type UserContextType = {
  isAdmin: boolean | null;
  loading: boolean;
  likedSongsLoading: boolean;
  likedSongIds: Set<string>;
  likedPlaylistIds: Set<string>;
  checkIsAdmin: () => Promise<boolean | null>;
  fetchLikedSongs: () => Promise<void>;
  fetchLikedPlaylists: () => Promise<void>;
  toggleLike: (songId: string, e?: React.MouseEvent) => Promise<void>;
  togglePlaylistLike: (playlistId: string) => Promise<void>;
};

const UserContext = createContext<UserContextType | undefined>(undefined);

export const useUser = () => {
  const context = useContext(UserContext);
  if (!context) {
    throw new Error("useUser must be used within a UserProvider");
  }
  return context;
};

export const UserProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const router = useRouter();
  const pathname = usePathname();
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);
  const [likedSongsLoading, setLikedSongsLoading] = useState(true);
  const [likedSongIds, setLikedSongIds] = useState<Set<string>>(new Set());
  const [likedPlaylistIds, setLikedPlaylistIds] = useState<Set<string>>(new Set());
  const [isCheckingAuth, setIsCheckingAuth] = useState(true);

  useEffect(() => {
    const authDetails = localStorage.getItem("auth_details");
    const isAuthPage = pathname?.startsWith("/auth");

    if (!authDetails) {
      if (!isAuthPage) {
        router.push("/auth/login");
      } else {
        setIsCheckingAuth(false);
      }
    } else {
      setIsCheckingAuth(false);
    }
  }, [pathname, router]);

  useEffect(() => {
    const authDetails = localStorage.getItem("auth_details");
    if (authDetails) {
      checkIsAdmin();
      fetchLikedSongs();
      fetchLikedPlaylists();
    }
  }, []);

  const checkIsAdmin = async (): Promise<boolean | null> => {
    setLoading(true);
    try {
      const res = await api.get<boolean>("/api/v1/user/isAdmin");
      // Backend returns boolean directly
      setIsAdmin(res.data);
      return res.data;
    } catch (err) {
      console.error("Failed to check admin status:", err);
    } finally {
      setLoading(false);
    }

    return null;
  };

  const fetchLikedSongs = async () => {
    try {
      setLikedSongsLoading(true);
      const authDetails = localStorage.getItem("auth_details");
      if (!authDetails) {
        setLikedSongsLoading(false);
        return;
      }
      
      const user = JSON.parse(authDetails);
      if (!user || !user.id) {
        setLikedSongsLoading(false);
        return;
      }

      const playlistId = `LIKED_SONGS_${user.id}`;
      const res = await api.get<ApiResponseDTO<EachSongDTO[]>>(`/api/v1/playlist/${playlistId}/songs`);
      
      if (res.data.success) {
        const ids = new Set(res.data.data.map(song => song.id));
        setLikedSongIds(ids);
      }
    } catch (error) {
      console.error("Failed to fetch liked songs:", error);
    } finally {
      setLikedSongsLoading(false);
    }
  };

  const fetchLikedPlaylists = async () => {
    try {
      const authDetails = localStorage.getItem("auth_details");
      if (!authDetails) return;
      
      const user = JSON.parse(authDetails);
      if (!user || !user.id) return;

      const res = await api.get<ApiResponseDTO<UserProfileDTO>>(`/api/v1/user/${user.id}`);
      
      if (res.data.success) {
        const ids = new Set(res.data.data.likedPlaylistsPreview.map(playlist => playlist.id));
        setLikedPlaylistIds(ids);
      }
    } catch (error) {
      console.error("Failed to fetch liked playlists:", error);
    }
  };

  const togglePlaylistLike = async (playlistId: string) => {
    const isLiking = !likedPlaylistIds.has(playlistId);
    
    // Optimistic update
    setLikedPlaylistIds(prev => {
      const next = new Set(prev);
      if (isLiking) next.add(playlistId);
      else next.delete(playlistId);
      return next;
    });

    try {
      if (isLiking) {
        await api.put(`/api/v1/playlist/${playlistId}/like`);
        toast.success("Added to Liked Playlists");
      } else {
        await api.delete(`/api/v1/playlist/${playlistId}/like`);
        toast.success("Removed from Liked Playlists");
      }
    } catch (error) {
      console.error("Failed to toggle playlist like:", error);
      toast.error(isLiking ? "Failed to like playlist" : "Failed to unlike playlist");
      
      // Revert optimistic update
      setLikedPlaylistIds(prev => {
        const next = new Set(prev);
        if (isLiking) next.delete(playlistId);
        else next.add(playlistId);
        return next;
      });
    }
  };

  const toggleLike = async (songId: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();

    const isLiking = !likedSongIds.has(songId);
    
    // Optimistic update
    setLikedSongIds(prev => {
      const next = new Set(prev);
      if (isLiking) next.add(songId);
      else next.delete(songId);
      return next;
    });

    try {
      const authDetails = localStorage.getItem("auth_details");
      if (!authDetails) return;
      
      const user = JSON.parse(authDetails);
      if (!user || !user.id) return;

      const playlistId = `LIKED_SONGS_${user.id}`;

      if (isLiking) {
        await api.put(`/api/v1/playlist/${playlistId}/song/${songId}`);
        toast.success("Added to Liked Songs");
      } else {
        // Try to remove - assuming DELETE endpoint exists or similar
        // If DELETE is not supported, we might need another way or just accept it's only adding for now.
        // Based on typical REST patterns:
        await api.delete(`/api/v1/playlist/${playlistId}/song/${songId}`);
        toast.success("Removed from Liked Songs");
      }
    } catch (error) {
      console.error("Failed to toggle like:", error);
      toast.error(isLiking ? "Failed to add to Liked Songs" : "Failed to remove from Liked Songs");
      
      // Revert optimistic update
      setLikedSongIds(prev => {
        const next = new Set(prev);
        if (isLiking) next.delete(songId);
        else next.add(songId);
        return next;
      });
    }
  };

  return (
    <UserContext.Provider
      value={{
        isAdmin,
        loading,
        likedSongsLoading,
        likedSongIds,
        likedPlaylistIds,
        checkIsAdmin,
        fetchLikedSongs,
        fetchLikedPlaylists,
        toggleLike,
        togglePlaylistLike,
      }}
    >
      <div>
        {children}
        {isCheckingAuth && (
          <div className="fixed inset-0 z-[100] flex justify-center items-center bg-black">
            <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-gray-600"></div>
          </div>
        )}
      </div>
    </UserContext.Provider>
  );
};