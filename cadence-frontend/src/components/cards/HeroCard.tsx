import React from "react";
import { LucideIcon } from "lucide-react";

interface HeroCardProps {
  title: string;
  icon: LucideIcon;
  onClick: () => void;
  gradient: string;
}

export const HeroCard: React.FC<HeroCardProps> = ({ title, icon: Icon, onClick, gradient }) => {
  return (
    <div 
      onClick={onClick}
      className={`relative h-20 sm:h-24 rounded-lg overflow-hidden cursor-pointer group transition-all duration-300 hover:scale-[1.02] shadow-lg ${gradient}`}
    >
      <div className="absolute inset-0 bg-black/20 group-hover:bg-transparent transition-colors" />
      <div className="flex items-center h-full px-6 gap-4 relative z-10">
        <div className="p-3 bg-black/30 rounded-full shadow-lg backdrop-blur-sm group-hover:scale-110 transition-transform duration-300">
            <Icon className="w-6 h-6 sm:w-8 sm:h-8 text-white fill-white/20" />
        </div>
        <h3 className="text-xl sm:text-2xl font-bold text-white tracking-tight group-hover:translate-x-1 transition-transform">{title}</h3>
      </div>
    </div>
  );
};
