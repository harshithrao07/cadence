import React from "react";
import Image from "next/image";
import { User } from "lucide-react";
import { ArtistPreviewDTO } from "@/types/Artists";

interface ArtistCardProps {
  artist: ArtistPreviewDTO;
  onClick: () => void;
  className?: string;
}

export const ArtistCard: React.FC<ArtistCardProps> = ({ artist, onClick, className }) => (
    <div 
      onClick={onClick}
      className={`group/card p-4 rounded-lg bg-zinc-900/40 hover:bg-zinc-800 transition cursor-pointer flex flex-col items-center gap-4 ${className || ""}`}
    >
      <div className="relative w-32 h-32 rounded-full overflow-hidden shadow-lg group-hover/card:scale-105 transition duration-300">
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
