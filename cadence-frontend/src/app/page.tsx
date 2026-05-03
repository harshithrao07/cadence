"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import api from "@/lib/api";
import { DiscoverDTO } from "@/types/Generic";
import { usePlayer } from "@/context/PlayerContext";
import { SongCard } from "@/components/cards/SongCard";
import { RecordCard } from "@/components/cards/RecordCard";
import { ArtistCard } from "@/components/cards/ArtistCard";
import { Section } from "@/components/Section";
import { HeroCard } from "@/components/cards/HeroCard";
import { Heart, HistoryIcon } from "lucide-react";

export default function Home() {
  const [data, setData] = useState<DiscoverDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [userId, setUserId] = useState<string | null>(null);
  const router = useRouter();
  const { playSong } = usePlayer();

  useEffect(() => {
    fetchDiscoverData();
    const authDetails = localStorage.getItem("auth_details");
    if (authDetails) {
      try {
        const { id } = JSON.parse(authDetails);
        setUserId(id);
      } catch (e) {
        console.error("Error parsing auth details:", e);
      }
    }
  }, []);

  const fetchDiscoverData = async () => {
    try {
      setLoading(true);
      const res = await api.get<{ data: DiscoverDTO }>("/api/v1/discover");
      setData(res.data.data);
    } catch (error) {
      console.error("Failed to fetch discover data:", error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-red-500"></div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="min-h-screen bg-black text-white flex items-center justify-center">
        <p className="text-zinc-500">Failed to load content</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen text-white pb-24">
      <div className="px-6 sm:px-12 pt-24 pb-8 space-y-12">
        {/* Header */}
        <header className="flex items-center justify-between mb-8">
            <h1 className="text-3xl font-bold tracking-tight">Discover</h1>
        </header>

        {/* Hero Cards: Liked Songs & Recently Played */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {userId && (
            <HeroCard
              title="Liked Songs"
              icon={Heart}
              onClick={() => router.push(`/playlist/LIKED_SONGS_${userId}`)}
              gradient="bg-gradient-to-br from-pink-300 to-rose-300 border border-white/40 shadow-xl hover:shadow-2xl hover:shadow-pink-300/20"
            />
          )}
          
          <HeroCard
            title="Recently Played"
            icon={HistoryIcon}
            onClick={() => router.push("/section/recently-played")}
            gradient="bg-gradient-to-br from-purple-500 to-violet-500 border border-white/40 shadow-xl hover:shadow-2xl hover:shadow-purple-500/20"
          />
        </div>

        {/* Suggested Artists */}
        {data.suggestedArtists && data.suggestedArtists.length > 0 && (
          <Section 
            title="Suggested Artists"
            onViewMore={() => router.push("/section/suggested-artists")}
            className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
          >
             {data.suggestedArtists.map(artist => (
               <ArtistCard key={artist.id} artist={artist} onClick={() => router.push(`/artists/${artist.id}`)} />
             ))}
          </Section>
        )}

        {/* New Releases */}
        {data.newReleases.length > 0 && (
          <Section 
            title="New Releases"
            onViewMore={() => router.push("/section/new-releases")}
            className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
          >
            {data.newReleases.slice(0, 6).map(record => (
              <RecordCard 
                key={record.id} 
                record={record} 
                onClick={() => router.push(`/records/${record.id}`)} 
              />
            ))}
          </Section>
        )}

        {/* Trending Songs */}
        {data.trendingSongs.length > 0 && (
          <Section 
            title="Trending Songs"
            onViewMore={() => router.push("/section/trending-songs")}
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4"
          >
            {data.trendingSongs.slice(0, 8).map(song => (
              <SongCard 
                key={song.id} 
                song={song} 
                onClick={() => playSong(song)} 
              />
            ))}
          </Section>
        )}

        {/* Popular Artists */}
        {data.popularArtists.length > 0 && (
          <Section 
            title="Popular Artists"
            onViewMore={() => router.push("/section/popular-artists")}
            className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
          >
             {data.popularArtists.map(artist => (
               <ArtistCard key={artist.id} artist={artist} onClick={() => router.push(`/artists/${artist.id}`)} />
             ))}
          </Section>
        )}

        {/* New Releases from Artists You Follow */}
        {data.newReleasesOfFollowingArtists.length > 0 && (
          <Section 
            title="New From Artists You Follow"
            onViewMore={() => router.push("/section/following-releases")}
            className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
          >
            {data.newReleasesOfFollowingArtists.map(record => (
              <RecordCard 
                key={record.id} 
                record={record} 
                onClick={() => router.push(`/records/${record.id}`)} 
              />
            ))}
          </Section>
        )}

        {/* Recommended Songs */}
        {data.recommendedSongs.length > 0 && (
          <Section 
            title="Recommended for You"
            onViewMore={() => router.push("/section/recommended-songs")}
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4"
          >
            {data.recommendedSongs.map(song => (
              <SongCard 
                key={song.id} 
                song={song} 
                onClick={() => playSong(song)} 
              />
            ))}
          </Section>
        )}
      </div>
    </div>
  );
}
