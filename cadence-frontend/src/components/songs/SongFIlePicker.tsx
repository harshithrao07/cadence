import { Music, Upload } from "lucide-react";
import { toast } from "sonner";

export const SongFilePicker = ({ onFileSelect, selectedFile }) => {
  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const maxSizeBytes = 10 * 1024 * 1024;
    if (file.size > maxSizeBytes) {
      toast.error("File size cannot exceed 10MB");
      e.target.value = "";
      return;
    }

    const audio = new Audio();
    const objectUrl = URL.createObjectURL(file);
    audio.src = objectUrl;

    audio.onloadedmetadata = () => {
      onFileSelect(file, Math.floor(audio.duration));
      URL.revokeObjectURL(objectUrl);
    };

    audio.onerror = () => {
      console.error("Invalid audio file");
      URL.revokeObjectURL(objectUrl);
    };
  };

  return (
    <div className="mb-6">
      <label className="block text-sm font-semibold mb-3 text-white">
        Audio File
      </label>
      
                <label className="relative flex items-center justify-center w-full h-32 border-2 border-dashed border-zinc-700 rounded-lg cursor-pointer hover:border-red-500 hover:bg-zinc-800/50 transition-all group">
        <input
          type="file"
          accept="audio/mpeg,audio/mp3"
          onChange={handleFileChange}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        />
        
        <div className="flex flex-col items-center gap-2">
          {selectedFile ? (
            <>
              <Music className="w-8 h-8 text-red-500" />
              <div className="text-sm font-medium text-white">{selectedFile.name}</div>
              <div className="text-xs text-zinc-400">Click to change file</div>
            </>
          ) : (
            <>
              <Upload className="w-8 h-8 text-zinc-400 group-hover:text-red-500 transition-colors" />
              <div className="text-sm font-medium text-zinc-300">Upload audio file</div>
              <div className="text-xs text-zinc-500">MP3 format supported</div>
            </>
          )}
        </div>
      </label>
    </div>
  );
};
