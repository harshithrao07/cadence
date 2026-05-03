import React from "react";
import Image from "next/image";
import { Play, Music } from "lucide-react";
import { EachSongDTO } from "@/types/Song";
import { formatDuration } from "@/lib/utility";

interface SongCardProps {
  song: EachSongDTO;
  onClick: () => void;
  className?: string;
}

export const SongCard: React.FC<SongCardProps> = ({ song, onClick, className }) => (
  <div 
    onClick={onClick}
    className={`group/card flex items-center gap-3 p-2 rounded-md hover:bg-white/10 transition cursor-pointer ${className || ""}`}
  >
    <div className="relative w-12 h-12 sm:w-16 sm:h-16 rounded overflow-hidden bg-zinc-800 flex-shrink-0">
      {song.recordPreviewWithCoverImageDTO?.coverUrl ? (
        <Image src={song.recordPreviewWithCoverImageDTO.coverUrl} alt={song.title} fill className="object-cover" />
      ) : (
        <div className="w-full h-full flex items-center justify-center">
            <Music className="w-6 h-6 text-zinc-600" />
        </div>
      )}
      <div className="absolute inset-0 bg-black/40 hidden group-hover/card:flex items-center justify-center">
         <Play className="w-5 h-5 text-white fill-white" />
      </div>
    </div>
    <div className="flex-1 min-w-0">
       <div className="font-medium text-white truncate group-hover/card:text-red-500 transition-colors">{song.title}</div>
       <div className="text-sm text-zinc-400 truncate">
          {song.artists?.map(a => a.name).join(", ") || "Unknown Artist"}
       </div>
    </div>
    <div className="text-xs text-zinc-500">
        {formatDuration(song.totalDuration)}
    </div>
  </div>
);
