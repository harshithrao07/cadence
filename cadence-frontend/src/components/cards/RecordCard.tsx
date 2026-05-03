import React from "react";
import Image from "next/image";
import { Play, Disc } from "lucide-react";
import { RecordPreviewDTO } from "@/types/Record";

interface RecordCardProps {
  record: RecordPreviewDTO;
  onClick: () => void;
  className?: string;
}

export const RecordCard: React.FC<RecordCardProps> = ({ record, onClick, className }) => (
  <div 
    onClick={onClick}
    className={`group/card p-4 rounded-lg bg-zinc-900/40 hover:bg-zinc-800 transition cursor-pointer flex flex-col gap-4 ${className || ""}`}
  >
    <div className="relative aspect-square w-full rounded-md overflow-hidden shadow-lg bg-zinc-800">
      {record.coverUrl ? (
        <Image src={record.coverUrl} alt={record.title} fill className="object-cover group-hover/card:scale-105 transition duration-300" />
      ) : (
        <div className="w-full h-full flex items-center justify-center">
            <Disc className="w-12 h-12 text-zinc-600" />
        </div>
      )}
      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover/card:opacity-100 transition flex items-center justify-center">
         <div className="bg-red-500 rounded-full p-3 shadow-lg transform translate-y-2 group-hover/card:translate-y-0 transition">
            <Play className="w-6 h-6 text-white fill-white ml-1" />
         </div>
      </div>
    </div>
    <div>
      <div className="font-bold text-white truncate">{record.title}</div>
      <div className="text-sm text-zinc-400 mt-1 line-clamp-1">
         {record.recordArtists?.map(a => a.name).join(", ") || "Unknown Artist"}
      </div>
      <div className="text-xs text-zinc-500 mt-1 capitalize">{record.recordType?.toLowerCase() || "Album"}</div>
    </div>
  </div>
);
