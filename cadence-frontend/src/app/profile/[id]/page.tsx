"use client";

import React, { useEffect, useState } from "react";
import { User, Mail, Music, Edit2, X, AlertCircle, CheckCircle } from "lucide-react";
import Image from "next/image";
import api from "@/lib/api";
import { UserProfileDTO } from "@/types/User";
import { ArtistPreviewDTO } from "@/types/Artists";
import { PlaylistPreviewDTO } from "@/types/Playlist";
import { useParams, useRouter } from "next/navigation";
import { ApiResponseDTO } from "@/types/ApiResponse";
import { toast } from "sonner";
import ProfilePictureSelector from "@/components/auth/ProfilePictureSelector";
import { MetadataDTO, HTTPMethod } from "@/types/Aws";
import axios from "axios";

export default function UserProfilePage() {
  const router = useRouter();
  const params = useParams();
  const profileId = params?.id as string;

  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"overview" | "playlists" | "artists">("overview");
  const [user, setUser] = useState<UserProfileDTO | null>(null);
  const [showEditModal, setShowEditModal] = useState(false);
  const [isOwner, setIsOwner] = useState(false);
  const [isVerifyingEmail, setIsVerifyingEmail] = useState(false);

  useEffect(() => {
    if (profileId) {
      fetchUserProfile();
      checkOwnership();
    }
  }, [profileId]);

  const checkOwnership = () => {
    const authDetails = localStorage.getItem("auth_details");
    if (authDetails) {
      try {
        const { id: loggedInUserId } = JSON.parse(authDetails);
        setIsOwner(loggedInUserId === profileId);
      } catch (e) {
        console.error("Error parsing auth details:", e);
      }
    }
  };

  const fetchUserProfile = async () => {
    try {
      setLoading(true);
      const res = await api.get<ApiResponseDTO<UserProfileDTO>>(`/api/v1/user/${profileId}`);
      if (res.data.data) {
        setUser(res.data.data);
        if (typeof res.data.data.isOwner === 'boolean') {
          setIsOwner(res.data.data.isOwner);
        }
      }
    } catch (error) {
      console.error("Failed to fetch user profile:", error);
      toast.error("Failed to load profile");
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyEmail = async () => {
    try {
        setIsVerifyingEmail(true);
        const res = await api.get<ApiResponseDTO<boolean>>("/api/v1/email/generate-verification-token");
        if (res.data.success) {
            toast.success("Verification email sent! Link valid for 24 hours.");
        } else {
            toast.error(res.data.message || "Failed to send verification email. Please try again later.");
        }
    } catch (error: any) {
        console.error("Failed to verify email:", error);
        if (!error.response) {
            toast.error("Failed to send verification email. Please try again later.");
        }
    } finally {
        setIsVerifyingEmail(false);
    }
  };

  const handleUpdateProfile = async (newName: string, newFile: File | null, isDeletePic: boolean) => {
    try {
      if (!user) return;

      // 1. Update Name
      if (newName !== user.name) {
        await api.put(`/api/v1/user/${profileId}`, { name: newName });
      }

      // 2. Handle Profile Pic
      if (newFile) {
         const formData = new FormData();
         formData.append("file", newFile, `users profile_url ${profileId}`);
         
         await api.post("/api/v1/files", formData, {
            headers: { "Content-Type": "multipart/form-data" },
         });
      } else if (isDeletePic) {
         const metadata: MetadataDTO = {
            category: "users",
            subCategory: "profile_url",
            primaryKey: profileId,
            httpMethod: HTTPMethod.DELETE
         };
         
         const res = await api.post<string>("/api/v1/files/presigned-url", metadata);
         if (res.status === 200 && res.data) {
            // The response data is the presigned URL directly? Or inside data? 
            // Based on previous code "await axios.delete(res.data)", assuming res.data is the url string.
            // Check api.post generic type <string> implies res.data is string.
            await axios.delete(res.data);
          }
      }

      toast.success("Profile updated successfully");
      if (newFile || isDeletePic) {
        toast.info("Changes may take up to 5 minutes to reflect due to caching");
      }
      fetchUserProfile();
      setShowEditModal(false);
    } catch (error) {
      console.error("Failed to update profile:", error);
      toast.error("Failed to update profile");
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-red-500"></div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="min-h-screen bg-black text-white flex flex-col items-center justify-center gap-4">
        <h1 className="text-2xl font-bold">Profile not found</h1>
        <button 
          onClick={() => router.push("/")}
          className="px-4 py-2 bg-red-500 rounded-full hover:bg-red-600 transition"
        >
          Go Home
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen text-white pb-24">
      {/* Header */}
      <div className="pt-20 pb-8 px-6 sm:px-12">
        <div className="flex flex-col md:flex-row items-center md:items-end gap-8">
            <div className="relative w-40 h-40 sm:w-52 sm:h-52 shadow-2xl rounded-full overflow-hidden border-2 border-white">
            {user.profileUrl ? (
                <Image
                src={user.profileUrl}
                alt={user.name}
                fill
                className="object-cover"
                />
            ) : (
                <div className="w-full h-full bg-black flex items-center justify-center">
                  <span className="text-6xl sm:text-7xl font-bold text-zinc-500">
                    {user.name.charAt(0).toUpperCase()}
                  </span>
                </div>
            )}
            </div>
            
            <div className="flex-1 text-center md:text-left">
            <div className="flex items-center justify-center md:justify-between gap-4">
               <h2 className="text-sm font-bold uppercase tracking-wider text-white/70 mb-2">Profile</h2>
               {isOwner && (
                 <button 
                   onClick={() => setShowEditModal(true)}
                   className="flex items-center gap-2 px-4 py-2 bg-white/10 hover:bg-white/20 rounded-full text-sm font-medium transition"
                 >
                   <Edit2 className="w-4 h-4" />
                   Edit Profile
                 </button>
               )}
            </div>
            <h1 className="text-4xl sm:text-6xl md:text-7xl font-black mb-6 text-white tracking-tight">
                {user.name}
            </h1>
            
            <div className="flex flex-wrap items-center justify-center md:justify-start gap-4 text-sm text-zinc-300">
                {isOwner && (
                    <div className="flex items-center gap-2 bg-white/5 px-3 py-1.5 rounded-full">
                        <Mail className="w-4 h-4 text-red-500" />
                        <span>{user.email}</span>
                    </div>
                )}
                {!user.emailVerified && isOwner && (
                    <button 
                        onClick={handleVerifyEmail}
                        disabled={isVerifyingEmail}
                        className="flex items-center gap-2 bg-yellow-500/10 hover:bg-yellow-500/20 px-3 py-1.5 rounded-full text-yellow-500 transition border border-yellow-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isVerifyingEmail ? (
                            <div className="w-4 h-4 border-2 border-yellow-500/30 border-t-yellow-500 rounded-full animate-spin" />
                        ) : (
                            <AlertCircle className="w-4 h-4" />
                        )}
                        <span>{isVerifyingEmail ? "Sending..." : "Verify Email"}</span>
                    </button>
                )}
                {isOwner && user.emailVerified && (
                    <div className="flex items-center gap-2 bg-green-500/10 hover:bg-green-500/20 px-3 py-1.5 rounded-full text-green-500 transition border border-green-500/20">
                        <CheckCircle className="w-4 h-4" />
                        <span>Email Verified</span>
                    </div>
                )}
            </div>
            </div>
        </div>
      </div>

      {/* Navigation */}
      <div className="sticky top-0 z-10 backdrop-blur-md px-6 sm:px-12">
        <div className="flex items-center gap-8 overflow-x-auto no-scrollbar py-4">
          <TabButton active={activeTab === "overview"} onClick={() => setActiveTab("overview")} label="Overview" />
          <TabButton active={activeTab === "playlists"} onClick={() => setActiveTab("playlists")} label="Playlists" />
          <TabButton active={activeTab === "artists"} onClick={() => setActiveTab("artists")} label="Following" />
        </div>
      </div>

      {/* Content */}
      <div className="px-6 sm:px-12 py-8">
        {activeTab === "overview" && (
            <div className="space-y-12">
                {/* Created Playlists Preview */}
                <Section title="Created Playlists" onSeeAll={() => setActiveTab("playlists")}>
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
                        {[...user.createdPlaylistsPreview]
                            .sort((a, b) => (a.name === "Liked Songs" ? -1 : b.name === "Liked Songs" ? 1 : 0))
                            .slice(0, 5)
                            .map((playlist) => (
                                <PlaylistCard key={playlist.id} playlist={playlist} onClick={() => router.push(`/playlist/${playlist.id}`)} />
                        ))}
                        {user.createdPlaylistsPreview.length === 0 && <EmptyState message="No playlists created" />}
                    </div>
                </Section>

                 {/* Liked Playlists Preview */}
                 {isOwner && (
                    <Section title="Liked Playlists" onSeeAll={() => setActiveTab("playlists")}>
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
                            {user.likedPlaylistsPreview.slice(0, 5).map((playlist) => (
                                <PlaylistCard key={playlist.id} playlist={playlist} onClick={() => router.push(`/playlist/${playlist.id}`)} />
                            ))}
                            {user.likedPlaylistsPreview.length === 0 && <EmptyState message="No liked playlists" />}
                        </div>
                    </Section>
                 )}
                
                {/* Followed Artists Preview */}
                <Section title="Following" onSeeAll={() => setActiveTab("artists")}>
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-6">
                        {user.artistFollowing.slice(0, 6).map((artist) => (
                            <ArtistCard key={artist.id} artist={artist} onClick={() => router.push(`/artists/${artist.id}`)} />
                        ))}
                         {user.artistFollowing.length === 0 && <EmptyState message="Not following any artists" />}
                    </div>
                </Section>
            </div>
        )}

        {activeTab === "playlists" && (
            <div className="space-y-12">
                <Section title="Created Playlists">
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
                        {[...user.createdPlaylistsPreview]
                            .sort((a, b) => (a.name === "Liked Songs" ? -1 : b.name === "Liked Songs" ? 1 : 0))
                            .map((playlist) => (
                                <PlaylistCard key={playlist.id} playlist={playlist} onClick={() => router.push(`/playlist/${playlist.id}`)} />
                        ))}
                        {user.createdPlaylistsPreview.length === 0 && <EmptyState message="No playlists created" />}
                    </div>
                </Section>
                 {isOwner && (
                    <Section title="Liked Playlists">
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
                            {user.likedPlaylistsPreview.map((playlist) => (
                                <PlaylistCard key={playlist.id} playlist={playlist} onClick={() => router.push(`/playlist/${playlist.id}`)} />
                            ))}
                            {user.likedPlaylistsPreview.length === 0 && <EmptyState message="No liked playlists" />}
                        </div>
                    </Section>
                 )}
            </div>
        )}

        {activeTab === "artists" && (
             <Section title="Artists You Follow">
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-6">
                    {user.artistFollowing.map((artist) => (
                        <ArtistCard key={artist.id} artist={artist} onClick={() => router.push(`/artists/${artist.id}`)} />
                    ))}
                    {user.artistFollowing.length === 0 && <EmptyState message="Not following any artists" />}
                </div>
            </Section>
        )}
      </div>
      {showEditModal && user && (
        <EditProfileModal 
          user={user} 
          onClose={() => setShowEditModal(false)} 
          onUpdate={handleUpdateProfile} 
        />
      )}
    </div>
  );
}

const EditProfileModal = ({ 
  user, 
  onClose, 
  onUpdate 
}: { 
  user: UserProfileDTO, 
  onClose: () => void, 
  onUpdate: (name: string, file: File | null, isDeletePic: boolean) => Promise<void> 
}) => {
  const [name, setName] = useState(user.name);
  const [profilePic, setProfilePic] = useState<string | null>(user.profileUrl);
  const [fileInputRef, setFileInputRef] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSave = async () => {
      setIsSubmitting(true);
      // Check if deleted: user had url but current preview is null
      const isDeletePic = profilePic === null && !!user.profileUrl;
      
      // If profilePic is null, ensure we don't send a stale file reference
      const fileToUpload = profilePic ? fileInputRef : null;

      await onUpdate(name, fileToUpload, isDeletePic);
      setIsSubmitting(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className="bg-black border border-zinc-800 p-8 rounded-2xl w-full max-w-md space-y-6 relative animate-in fade-in zoom-in duration-200">
         <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-white transition">
            <X className="w-5 h-5" />
         </button>
         <h2 className="text-2xl font-bold text-center">Edit Profile</h2>
         
         <div className="flex justify-center">
            {/* @ts-ignore */}
            <ProfilePictureSelector 
                profilePic={profilePic} 
                setProfilePic={setProfilePic} 
                setFileInputRef={setFileInputRef} 
            />
         </div>
         
         <div>
            <label className="block text-sm text-zinc-400 mb-2">Name</label>
            <input 
                value={name} 
                onChange={e => setName(e.target.value)}
                className="w-full bg-black border border-zinc-800 rounded-lg px-4 py-3 text-white focus:border-red-500 outline-none transition"
                placeholder="Enter your name"
            />
         </div>
         
         <button 
            onClick={handleSave} 
            disabled={isSubmitting}
            className="w-full bg-red-500 hover:bg-red-600 text-white font-bold py-3 rounded-lg transition disabled:opacity-50 flex items-center justify-center gap-2"
         >
            {isSubmitting && <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />}
            {isSubmitting ? "Saving..." : "Save Changes"}
         </button>
      </div>
    </div>
  )
}

// Sub-components

const TabButton = ({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) => (
  <button
    onClick={onClick}
    className={`px-4 py-2 rounded-full text-sm font-medium transition-all ${
      active ? "bg-white text-black" : "text-zinc-400 hover:text-white hover:bg-white/10"
    }`}
  >
    {label}
  </button>
);

const Section = ({ title, children, onSeeAll }: { title: string; children: React.ReactNode; onSeeAll?: () => void }) => (
  <div className="space-y-4">
    <div className="flex items-center justify-between">
      <h3 className="text-2xl font-bold text-white">{title}</h3>
      {onSeeAll && (
        <button onClick={onSeeAll} className="text-sm font-bold text-zinc-400 hover:text-white hover:underline uppercase tracking-wider">
          See All
        </button>
      )}
    </div>
    {children}
  </div>
);

const ArtistCard = ({ artist, onClick }: { artist: ArtistPreviewDTO; onClick: () => void }) => (
  <div 
    onClick={onClick}
    className="group p-4 rounded-xl bg-zinc-900/50 hover:bg-zinc-800/80 transition cursor-pointer flex flex-col items-center gap-4"
  >
    <div className="relative w-32 h-32 rounded-full overflow-hidden shadow-lg group-hover:scale-105 transition duration-300">
      {artist.profileUrl ? (
        <Image src={artist.profileUrl} alt={artist.name} fill className="object-cover" />
      ) : (
        <div className="w-full h-full bg-zinc-800 flex items-center justify-center">
            <User className="w-12 h-12 text-zinc-500" />
        </div>
      )}
    </div>
    <div className="text-center">
      <div className="font-bold text-white truncate w-full">{artist.name}</div>
      <div className="text-sm text-zinc-400 mt-1">Artist</div>
    </div>
  </div>
);

const PlaylistCard = ({ playlist, onClick }: { playlist: PlaylistPreviewDTO; onClick: () => void }) => {
    const isLikedSongs = playlist.name === "Liked Songs";
    const coverUrl = isLikedSongs ? "/images/playlists/liked-songs.jpg" : playlist.coverUrl;

    return (
        <div onClick={onClick} className="group p-4 rounded-xl bg-zinc-900/50 hover:bg-zinc-800/80 transition cursor-pointer flex flex-col gap-4">
            <div className="relative aspect-square w-full rounded-md overflow-hidden shadow-lg bg-zinc-800">
                {coverUrl ? (
                    <Image src={coverUrl} alt={playlist.name} fill className="object-cover group-hover:scale-105 transition duration-300" />
                ) : (
                    <div className="w-full h-full flex items-center justify-center">
                        <Music className="w-12 h-12 text-zinc-600" />
                    </div>
                )}
                {playlist.visibility && (
                    <div className="absolute top-2 right-2 bg-black/60 backdrop-blur-md text-white text-[10px] px-2 py-0.5 rounded-full border border-white/10 uppercase tracking-wider font-bold z-10">
                        {playlist.visibility}
                    </div>
                )}
            </div>
            <div>
                <div className="font-bold text-white truncate">{playlist.name}</div>
                <div className="text-sm text-zinc-400 mt-1 line-clamp-2">By {playlist.owner.name}</div>
            </div>
        </div>
    )
}

const EmptyState = ({ message }: { message: string }) => (
    <div className="col-span-full py-12 text-center text-zinc-500">
        <p>{message}</p>
    </div>
);
