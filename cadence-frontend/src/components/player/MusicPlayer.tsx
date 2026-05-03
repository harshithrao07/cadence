"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { usePlayer } from "@/context/PlayerContext";
import { useUser } from "@/context/UserContext";
import { Play, Pause, SkipBack, SkipForward, Volume2, VolumeX, Maximize2, ListMusic, Heart, X, Trash2, Loader2 } from "lucide-react";
import Image from "next/image";

const formatTime = (time: number) => {
    if (isNaN(time)) return "0:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes}:${seconds.toString().padStart(2, "0")}`;
};

export const MusicPlayer: React.FC = () => {
    const pathname = usePathname();
    const {
        currentSong,
        isPlaying,
        isBuffering,
        currentTime,
        duration,
        volume,
        queue,
        queueIndex,
        togglePlay,
        seekTo,
        setVolume,
        playNext,
        playPrevious,
        playQueue,
        removeFromQueue
    } = usePlayer();
    
    const { likedSongIds, toggleLike } = useUser();

    const [showQueue, setShowQueue] = React.useState(false);
    const [showFullscreen, setShowFullscreen] = React.useState(false);
    const [previousVolume, setPreviousVolume] = React.useState<number | null>(null);

    const isMuted = volume === 0;
    const progress = duration ? Math.min(100, Math.max(0, (currentTime / duration) * 100)) : 0;
    const volumePercent = Math.min(100, Math.max(0, volume * 100));

    React.useEffect(() => {
        if (!showFullscreen) return;
        if (typeof document === "undefined") return;

        const originalOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        return () => {
            document.body.style.overflow = originalOverflow;
        };
    }, [showFullscreen]);

    if (pathname?.startsWith("/auth")) return null;

    if (!currentSong) return null;

    let currentIndex = queueIndex;
    if ((currentIndex === undefined || currentIndex < 0) && queue.length > 0) {
        currentIndex = 0;
    }

    const hasQueue = queue.length > 0 && currentIndex >= 0 && currentIndex < queue.length;
    const nowPlayingSong = hasQueue ? queue[currentIndex] : currentSong;
    const upcomingQueue = hasQueue ? queue.slice(currentIndex + 1) : [];

    const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
        seekTo(parseFloat(e.target.value));
    };

    const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setVolume(parseFloat(e.target.value));
    };

    const handleToggleMute = () => {
        if (isMuted) {
            const restore = previousVolume !== null && previousVolume > 0 ? previousVolume : 0.5;
            setVolume(restore);
            setPreviousVolume(null);
        } else {
            setPreviousVolume(volume);
            setVolume(0);
        }
    };

    return (
        <>
        <div className="fixed bottom-0 left-0 right-0 h-24 bg-gradient-to-r from-black/95 via-zinc-950/95 to-black/95 backdrop-blur-lg border-t border-zinc-800 px-4 flex items-center justify-between z-50">
            {/* Song Info */}
            <div className="flex items-center gap-4 w-[30%]">
                <div className="relative w-14 h-14 flex-shrink-0">
                    <Image
                        src={currentSong.recordPreviewWithCoverImageDTO?.coverUrl || "/images/records/record-placeholder.png"}
                        alt={currentSong.title}
                        fill
                        className="rounded shadow-lg object-cover"
                    />
                </div>
                <div className="min-w-0">
                    <h4 className="text-white font-medium truncate">{currentSong.title}</h4>
                    <p className="text-gray-400 text-xs truncate">
                        {currentSong.artists?.map(a => a.name).join(", ") || "Unknown Artist"}
                    </p>
                </div>
            </div>

            <div className="flex flex-col items-center gap-2 w-[40%]">
                <div className="flex items-center gap-6">
                    <button
                        className="text-gray-400 hover:text-white transition"
                        onClick={playPrevious}
                    >
                        <SkipBack className="w-5 h-5 fill-current" />
                    </button>
                    <button
                        onClick={togglePlay}
                        className="w-8 h-8 rounded-full bg-white flex items-center justify-center hover:scale-105 transition"
                    >
                        {isBuffering ? (
                            <Loader2 className="w-5 h-5 text-black animate-spin" />
                        ) : isPlaying ? (
                            <Pause className="w-5 h-5 text-black fill-current" />
                        ) : (
                            <Play className="w-5 h-5 text-black fill-current ml-0.5" />
                        )}
                    </button>
                    <button
                        className="text-gray-400 hover:text-white transition"
                        onClick={playNext}
                    >
                        <SkipForward className="w-5 h-5 fill-current" />
                    </button>
                </div>

                <div className="flex items-center gap-2 w-full max-w-md">
                    <span className="text-[10px] text-gray-400 w-8 text-right">
                        {formatTime(currentTime)}
                    </span>
                    <input
                        type="range"
                        min="0"
                        max={duration || 0}
                        value={currentTime}
                        onChange={handleSeek}
                        className="flex-1 h-1 rounded-full appearance-none cursor-pointer"
                        style={{
                            backgroundImage: `linear-gradient(to right, #ef4444 0%, #ef4444 ${progress}%, #374151 ${progress}%, #374151 100%)`
                        }}
                    />
                    <span className="text-[10px] text-gray-400 w-8">
                        {formatTime(duration)}
                    </span>
                </div>
            </div>

            <div className="flex items-center justify-end gap-3 w-[30%]">
                <button
                    onClick={(e) => toggleLike(currentSong.id, e)}
                    className="p-1 rounded-full hover:bg-zinc-800 transition"
                >
                    <Heart className={`w-5 h-5 ${likedSongIds.has(currentSong.id) ? "text-red-500 fill-red-500" : "text-gray-400 hover:text-white"}`} />
                </button>
                <button
                    onClick={() => setShowQueue(v => !v)}
                    className={`p-1 rounded-full transition ${showQueue ? "bg-zinc-800 text-red-500" : "hover:bg-zinc-800 text-gray-400 hover:text-white"}`}
                >
                    <ListMusic className="w-5 h-5" />
                </button>
                <div className="flex items-center gap-2">
                    <button
                        onClick={handleToggleMute}
                        className="p-1 rounded-full hover:bg-zinc-800 transition"
                    >
                        {isMuted ? (
                            <VolumeX className="w-5 h-5 text-red-500" />
                        ) : (
                            <Volume2 className="w-5 h-5 text-gray-400 hover:text-white" />
                        )}
                    </button>
                    <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.01"
                        value={volume}
                        onChange={handleVolumeChange}
                        className="w-24 h-1 rounded-full appearance-none cursor-pointer accent-white hover:accent-red-500 transition"
                        style={{
                            backgroundImage: `linear-gradient(to right, #ef4444 0%, #ef4444 ${volumePercent}%, #374151 ${volumePercent}%, #374151 100%)`
                        }}
                    />
                </div>
                <button
                    onClick={() => setShowFullscreen(v => !v)}
                    className="p-1 rounded-full hover:bg-zinc-800 transition"
                >
                    <Maximize2 className="w-4 h-4 text-gray-400 hover:text-white cursor-pointer" />
                </button>
            </div>
        </div>
        {showQueue && hasQueue && (
            <div className="fixed top-0 bottom-24 right-0 w-96 bg-black border-l border-zinc-800 shadow-2xl overflow-hidden z-50 flex flex-col">
                <div className="flex items-center justify-between px-4 py-3 border-b border-zinc-800 bg-black">
                    <span className="text-xs font-semibold tracking-wide text-gray-300 uppercase">Queue</span>
                    <button
                        onClick={() => setShowQueue(false)}
                        className="p-1 rounded-full hover:bg-zinc-800 transition"
                    >
                        <X className="w-4 h-4 text-gray-400" />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto px-4 py-3 space-y-6">
                    <div>
                        <div className="text-xs font-semibold text-gray-400 uppercase mb-2">Now Playing</div>
                        <button
                            onClick={() => playQueue(queue, currentIndex)}
                            className="w-full flex items-center gap-3 px-2 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-left"
                        >
                            <div className="w-11 h-11 relative flex-shrink-0 rounded-md overflow-hidden bg-zinc-900">
                                <Image
                                    src={nowPlayingSong.recordPreviewWithCoverImageDTO?.coverUrl || "/images/records/record-placeholder.png"}
                                    alt={nowPlayingSong.title}
                                    fill
                                    className="object-cover"
                                />
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="text-sm font-semibold truncate text-red-500">
                                    {nowPlayingSong.title}
                                </div>
                                <div className="text-xs text-gray-400 truncate">
                                    {nowPlayingSong.artists?.map(a => a.name).join(", ") || "Unknown Artist"}
                                </div>
                            </div>
                            <div className="text-xs text-gray-400 pl-2">
                                {formatTime(nowPlayingSong.totalDuration || 0)}
                            </div>
                        </button>
                    </div>
                    {upcomingQueue.length > 0 && (
                        <div>
                            <div className="text-xs font-semibold text-gray-400 uppercase mb-2">Next in Queue</div>
                            <div className="space-y-1">
                                {upcomingQueue.map((song, idx) => {
                                    const absoluteIndex = (currentIndex ?? 0) + 1 + idx;
                                    return (
                                        <div key={song.id + absoluteIndex} className="group relative flex items-center gap-2">
                                            <button
                                                onClick={() => playQueue(queue, absoluteIndex)}
                                                className="flex-1 flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-zinc-900 text-left transition min-w-0"
                                            >
                                                <div className="w-9 h-9 relative flex-shrink-0 rounded-md overflow-hidden bg-zinc-900">
                                                    <Image
                                                        src={song.recordPreviewWithCoverImageDTO?.coverUrl || "/images/records/record-placeholder.png"}
                                                        alt={song.title}
                                                        fill
                                                        className="object-cover"
                                                    />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <div className="text-sm font-medium truncate text-white">
                                                        {song.title}
                                                    </div>
                                                    <div className="text-xs text-gray-400 truncate">
                                                        {song.artists?.map(a => a.name).join(", ") || "Unknown Artist"}
                                                    </div>
                                                </div>
                                                <div className="text-xs text-gray-500 pl-2">
                                                    {formatTime(song.totalDuration || 0)}
                                                </div>
                                            </button>
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    removeFromQueue(absoluteIndex);
                                                }}
                                                className="p-2 text-gray-400 hover:text-red-500 opacity-0 group-hover:opacity-100 transition"
                                                title="Remove from queue"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        )}
        {showFullscreen && (
            <div className="fixed inset-0 z-50 overflow-hidden">
                <div className="absolute inset-0">
                    <Image
                        src={currentSong.recordPreviewWithCoverImageDTO?.coverUrl || "/images/records/record-placeholder.png"}
                        alt={currentSong.title}
                        fill
                        className="object-cover blur-3xl scale-110 opacity-60"
                    />
                    <div className="absolute inset-0 bg-black/70" />
                </div>
                <button
                    onClick={() => setShowFullscreen(false)}
                    className="absolute top-4 right-4 z-20 p-2 rounded-full bg-black/40 hover:bg-black/60 transition"
                >
                    <X className="w-5 h-5 text-gray-100" />
                </button>
                <div className="relative z-10 flex flex-col items-center justify-center h-full px-6">
                    <div className="w-64 h-64 sm:w-80 sm:h-80 md:w-[22rem] md:h-[22rem] lg:w-[26rem] lg:h-[26rem] relative rounded-3xl overflow-hidden shadow-[0_25px_80px_rgba(0,0,0,0.8)] border border-white/10 bg-black/40">
                        <Image
                            src={currentSong.recordPreviewWithCoverImageDTO?.coverUrl || "/images/records/record-placeholder.png"}
                            alt={currentSong.title}
                            fill
                            className="object-cover"
                        />
                    </div>
                    <div className="mt-8 w-full max-w-xl flex flex-col items-center gap-6">
                        <div className="text-center">
                            <div className="text-2xl sm:text-3xl font-semibold text-white truncate">
                                {nowPlayingSong.title}
                            </div>
                            <div className="text-sm text-gray-400 mt-1 truncate">
                                {nowPlayingSong.artists?.map(a => a.name).join(", ") || "Unknown Artist"}
                            </div>
                        </div>
                        <div className="flex items-center gap-3 w-full">
                            <span className="text-xs text-gray-400 w-10 text-right">
                                {formatTime(currentTime)}
                            </span>
                            <input
                                type="range"
                                min="0"
                                max={duration || 0}
                                value={currentTime}
                                onChange={handleSeek}
                                className="flex-1 h-1 rounded-full appearance-none cursor-pointer"
                                style={{
                                    backgroundImage: `linear-gradient(to right, #ef4444 0%, #ef4444 ${progress}%, #374151 ${progress}%, #374151 100%)`
                                }}
                            />
                            <span className="text-xs text-gray-400 w-10">
                                {formatTime(duration)}
                            </span>
                        </div>
                        <div className="flex items-center justify-between w-full mt-2">
                            <div className="flex items-center gap-6">
                                <button
                                    className="text-gray-400 hover:text-white transition"
                                    onClick={playPrevious}
                                >
                                    <SkipBack className="w-6 h-6 fill-current" />
                                </button>
                                <button
                                    onClick={togglePlay}
                                    className="w-12 h-12 rounded-full bg-white flex items-center justify-center hover:scale-105 transition"
                                >
                                    {isPlaying ? (
                                        <Pause className="w-6 h-6 text-black fill-current" />
                                    ) : (
                                        <Play className="w-6 h-6 text-black fill-current ml-0.5" />
                                    )}
                                </button>
                                <button
                                    className="text-gray-400 hover:text-white transition"
                                    onClick={playNext}
                                >
                                    <SkipForward className="w-6 h-6 fill-current" />
                                </button>
                            </div>
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={handleToggleMute}
                                    className="p-1 rounded-full hover:bg-zinc-800 transition"
                                >
                                    {isMuted ? (
                                        <VolumeX className="w-5 h-5 text-red-500" />
                                    ) : (
                                        <Volume2 className="w-5 h-5 text-gray-400 hover:text-white" />
                                    )}
                                </button>
                                <input
                                    type="range"
                                    min="0"
                                    max="1"
                                    step="0.01"
                                    value={volume}
                                    onChange={handleVolumeChange}
                                    className="w-28 h-1 rounded-full appearance-none cursor-pointer accent-white hover:accent-red-500 transition"
                                    style={{
                                        backgroundImage: `linear-gradient(to right, #ef4444 0%, #ef4444 ${volumePercent}%, #374151 ${volumePercent}%, #374151 100%)`
                                    }}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        )}
        </>
    );
};
