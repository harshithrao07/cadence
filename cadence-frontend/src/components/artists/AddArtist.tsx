"use client";

import React, { useState } from "react";
import { X, Loader2 } from "lucide-react";
import { UpsertArtistDTO } from "../../types/Artists";
import api from "../../lib/api";
import ProfilePictureSelector from "@/components/auth/ProfilePictureSelector";
import { useArtists } from "@/context/ArtistContext";
import { MetadataDTO, HTTPMethod } from "@/types/Aws";
import axios from "axios";
import { toast } from "sonner";

interface AddArtistProps {
  setShowAddForm: (show: boolean) => void;
  initialArtist?: {
    id: string;
    name: string;
    description?: string;
    profileUrl?: string;
  };
  mode?: "create" | "edit";
  onArtistSaved?: (artist: {
    id: string;
    name: string;
    description?: string;
    profileUrl?: string | null;
  }) => void;
}

const AddArtist: React.FC<AddArtistProps> = ({
  setShowAddForm,
  initialArtist,
  mode = "create",
  onArtistSaved
}) => {
  const [newArtist, setNewArtist] = useState<UpsertArtistDTO>({
    name: initialArtist?.name || "",
    description: initialArtist?.description || "",
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [profilePic, setProfilePic] = useState(initialArtist?.profileUrl || null);
  const [fileInputRef, setFileInputRef] = useState<File>(null);
  const [loading, setLoading] = useState(false);

  const { refreshArtists } = useArtists();

  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    if (!newArtist.name.trim()) {
      newErrors.name = "Artist name is required";
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    setLoading(true);
    try {
      const payload = {
        id: initialArtist?.id || null,
        name: newArtist.name,
        description: newArtist.description,
      };

      const res = await api.post("/api/v1/artist/upsert", payload);

      if (res.status >= 200 && res.status < 300) {
        let artistId = initialArtist?.id;
        
        // If creating (no initial ID), get ID from response
        if (!artistId) {
            // Assuming response data is the ID (string) or object with ID
            if (typeof res.data.data === 'string') {
                artistId = res.data.data;
            } else if (res.data.data?.id) {
                artistId = res.data.data.id;
            }
        }

        if (!artistId) {
            console.error("Failed to get artist ID from response");
            return;
        }

        let profileUrl = initialArtist?.profileUrl || null;

        const hasNewFile = fileInputRef && profilePic;
        const hasDeletedFile = !profilePic && initialArtist?.profileUrl;

        if (hasNewFile) {
          const formData = new FormData();
          const filename = "artist profile_url " + artistId;

          formData.append("file", fileInputRef, filename);

          const fileRes = await api.post(`/api/v1/files`, formData, {
            headers: { "Content-Type": "multipart/form-data" },
          });

          if (Array.isArray(fileRes.data) && fileRes.data.length > 0) {
            profileUrl = fileRes.data[0].url;
          }
        } else if (hasDeletedFile) {
          profileUrl = null;
          
          try {
            const metadata: MetadataDTO = {
              category: "artist",
              subCategory: "profile_url",
              primaryKey: artistId,
              httpMethod: HTTPMethod.DELETE
            };
            
            const res = await api.post<string>("/api/v1/files/presigned-url", metadata);
            if (res.status === 200 && res.data) {
              const presignedUrl = res.data;
              await axios.delete(presignedUrl);
            }
          } catch (err) {
            console.error("Failed to delete artist profile picture:", err);
          }
        }

        const savedArtist = {
          id: artistId,
          name: newArtist.name,
          description: newArtist.description,
          profileUrl,
        };

        refreshArtists();
        if (onArtistSaved) {
          onArtistSaved(savedArtist);
        }

        toast.success(mode === "edit" ? "Artist updated successfully" : "Artist created successfully");
        if (hasNewFile || hasDeletedFile) {
          toast.info("Changes may take up to 5 minutes to reflect due to caching");
        }

        setNewArtist({ name: "", description: "" });
        setProfilePic(null);
        setShowAddForm(false);
        setErrors({});
      }
    } catch (error) {
      console.error("Error saving artist:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    setShowAddForm(false);
    setNewArtist({ name: "", description: "" });
    setProfilePic(null);
    setErrors({});
  };

  return (
    <div className="mb-10 bg-gradient-to-br from-zinc-900/90 to-zinc-900/50 backdrop-blur-xl rounded-2xl p-8 border border-zinc-800/50 shadow-2xl">
      <div className="flex items-center justify-between mb-8">
        <h3 className="text-3xl font-bold">
          {mode === "edit" ? "Edit Artist" : "Add New Artist"}
        </h3>
        <button
          onClick={handleCancel}
          className="text-zinc-400 hover:text-white transition-colors p-2 hover:bg-zinc-800 rounded-full"
        >
          <X className="w-6 h-6" />
        </button>
      </div>

      <div className="grid grid-cols-2">
        {/* Profile Image Upload */}
        <div className="flex flex-col items-center justify-center">
          <ProfilePictureSelector
            profilePic={profilePic}
            setProfilePic={setProfilePic}
            setFileInputRef={setFileInputRef}
          />
        </div>

        {/* Artist Name */}
        <div>
          <label className="block text-sm font-bold mb-3 text-zinc-300">
            Artist Name <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={newArtist.name}
            onChange={(e) =>
              setNewArtist({ ...newArtist, name: e.target.value })
            }
            className={`w-full bg-zinc-800/50 border-2 ${
              errors.name ? "border-red-500" : "border-zinc-800"
            } rounded-xl px-5 py-4 text-black focus:outline-none focus:border-red-500 focus:bg-zinc-800 transition-all text-lg placeholder-zinc-600`}
            placeholder="Enter artist name"
          />
          {errors.name && (
            <p className="text-red-400 text-sm mt-2 font-semibold">
              {errors.name}
            </p>
          )}

          <label className="block text-sm font-bold my-3 text-zinc-300">
            Description
          </label>
          <textarea
            value={newArtist.description}
            onChange={(e) =>
              setNewArtist({ ...newArtist, description: e.target.value })
            }
            className="w-full text-black bg-zinc-800/50 border-2 border-zinc-800 rounded-xl px-5 py-4 focus:outline-none focus:border-red-500 focus:bg-zinc-800 transition-all resize-none text-lg placeholder-zinc-600"
            rows={4}
            placeholder="Tell us about this artist..."
          />

          {/* Submit Buttons */}
          <div className="flex gap-4 pt-4">
            <button
              onClick={handleSubmit}
              disabled={loading}
              className="bg-gradient-to-r from-red-500 to-red-600 hover:from-red-600 hover:to-red-700 text-white px-10 py-4 rounded-full font-bold transition-all transform hover:scale-105 shadow-lg shadow-red-500/30 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  {mode === "edit" ? "Saving..." : "Adding..."}
                </>
              ) : mode === "edit" ? (
                "Save Changes"
              ) : (
                "Add Artist"
              )}
            </button>
            <button
              onClick={handleCancel}
              disabled={loading}
              className="bg-zinc-800 hover:bg-zinc-700 text-white px-10 py-4 rounded-full font-bold transition-all border border-zinc-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AddArtist;
