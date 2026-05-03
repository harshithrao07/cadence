"use client";

import React, { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import api from "@/lib/api";
import { ApiResponseDTO } from "@/types/ApiResponse";
import { DiscoverDTO } from "@/types/Generic";
import { SongCard } from "@/components/cards/SongCard";
import { RecordCard } from "@/components/cards/RecordCard";
import { ArtistCard } from "@/components/cards/ArtistCard";
import { usePlayer } from "@/context/PlayerContext";

export default function SectionPage() {
  const { type } = useParams();
  const router = useRouter();
  const { playSong, playQueue } = usePlayer();
  const [data, setData] = useState<DiscoverDTO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await api.get<ApiResponseDTO<DiscoverDTO>>("/api/v1/discover");
        if (res.data.success) {
          setData(res.data.data);
        }
      } catch (err) {
        console.error("Failed to fetch discovery data:", err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) {
    return <div className="p-8 text-white">Loading...</div>;
  }

  if (!data) {
    return <div className="p-8 text-white">Failed to load data.</div>;
  }

  const getSectionContent = () => {
    switch (type) {
      case "recently-played":
        if (data.recentlyPlayedSongs.length === 0) {
           return {
             title: "Recently Played",
             content: <div className="text-zinc-500">Nothing to display</div>,
           };
        }
        return {
          title: "Recently Played",
          content: (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
              {data.recentlyPlayedSongs.map((song, index) => (
                <SongCard key={song.id} song={song} onClick={() => playQueue(data.recentlyPlayedSongs.slice(index), 0)} />
              ))}
            </div>
          ),
        };
      case "suggested-artists":
        return {
          title: "Suggested Artists",
          content: (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
              {data.suggestedArtists.map((artist) => (
                <ArtistCard key={artist.id} artist={artist} onClick={() => router.push(`/artists/${artist.id}`)} />
              ))}
            </div>
          ),
        };
      case "new-releases":
        return {
          title: "New Releases",
          content: (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
              {data.newReleases.map((record) => (
                <RecordCard key={record.id} record={record} onClick={() => router.push(`/records/${record.id}`)} />
              ))}
            </div>
          ),
        };
      case "trending-songs":
        return {
            title: "Trending Songs",
            content: (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
                {data.trendingSongs.map((song, index) => (
                  <SongCard key={song.id} song={song} onClick={() => playQueue(data.trendingSongs.slice(index), 0)} />
                ))}
              </div>
            ),
        };
      case "popular-artists":
        return {
            title: "Popular Artists",
            content: (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                {data.popularArtists.map((artist) => (
                  <ArtistCard key={artist.id} artist={artist} onClick={() => router.push(`/artists/${artist.id}`)} />
                ))}
              </div>
            ),
        };
      case "following-releases":
        return {
            title: "New From Artists You Follow",
            content: (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                {data.newReleasesOfFollowingArtists.map((record) => (
                  <RecordCard key={record.id} record={record} onClick={() => router.push(`/records/${record.id}`)} />
                ))}
              </div>
            ),
        };
      case "recommended-songs":
        return {
            title: "Recommended for You",
            content: (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
                {data.recommendedSongs.map((song, index) => (
                  <SongCard key={song.id} song={song} onClick={() => playQueue(data.recommendedSongs.slice(index), 0)} />
                ))}
              </div>
            ),
        };
      default:
        return { title: "Section Not Found", content: <div>Section not found</div> };
    }
  };

  const { title, content } = getSectionContent();

  return (
    <div className="px-8 pt-24 pb-24 space-y-6 min-h-screen">
      <h1 className="text-3xl font-bold text-white mb-6">{title}</h1>
      {content}
    </div>
  );
}
