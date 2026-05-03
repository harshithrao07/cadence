import React from "react";

interface SectionProps {
  title: string;
  children: React.ReactNode;
  onViewMore?: () => void;
  className?: string;
}

export const Section: React.FC<SectionProps> = ({ title, children, onViewMore, className }) => {
  if (!children || (Array.isArray(children) && children.length === 0)) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold text-white">{title}</h2>
        </div>
        <div className="text-zinc-500 text-sm">Nothing to display</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-white">{title}</h2>
        {onViewMore && (
          <button 
            onClick={onViewMore}
            className="px-4 py-1.5 text-xs font-bold text-white bg-red-600 hover:bg-red-700 rounded-full transition-colors uppercase tracking-wider shadow-lg hover:shadow-red-600/20"
          >
            View More
          </button>
        )}
      </div>
      <div className={className}>
        {children}
      </div>
    </div>
  );
};
