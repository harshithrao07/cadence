import { blobToBase64 } from "@/lib/utility";
import { Button } from "@material-tailwind/react";
import React, { useEffect, useRef, useState } from "react";

export default function ProfilePictureSelector({
  profilePic,
  setProfilePic,
  setFileInputRef
}) {
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const fileInputRef = useRef(null);

  const handleImageChange = async (e) => {
    const file = e.target.files[0];
    if (file) {
      setFileInputRef(file);
      setProfilePic(URL.createObjectURL(file));
    }
  };

  const handleButtonClick = () => {
    fileInputRef.current?.click();
  };

  const handleDeleteImage = () => {
    setProfilePic(null);
    fileInputRef.current.value = null; // clear file input
  };

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        setIsPreviewOpen(false);
      }
    };

    if (isPreviewOpen) {
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", handleKeyDown);
    } else {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", handleKeyDown);
    }

    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isPreviewOpen]);

  return (
    <div className="relative flex flex-col text-center items-center space-y-4 p-6 border rounded-lg w-64 mx-auto">
      <div className="relative">
        {profilePic ? (
          <div className="p-[2px] rounded-full border-2 border-transparent">
            <img
              src={profilePic}
              alt="Profile Preview"
              className="w-32 h-32 rounded-full object-cover border cursor-pointer hover:scale-105 transition-transform duration-300 hover:brightness-75"
              onClick={() => setIsPreviewOpen(true)}
            />
          </div>
        ) : (
          <div
            onClick={handleButtonClick}
            className="cursor-pointer w-32 h-32 rounded-full border flex items-center justify-center text-gray-400 hover:bg-[#181818] transition-transform duration-300"
          >
            No Image
          </div>
        )}
      </div>

      {profilePic && (
        <Button
          size="sm"
          variant="outlined"
          color="white"
          className="flex items-center gap-3"
          onClick={handleButtonClick}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
            className="size-6"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M6.827 6.175A2.31 2.31 0 0 1 5.186 7.23c-.38.054-.757.112-1.134.175C2.999 7.58 2.25 8.507 2.25 9.574V18a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9.574c0-1.067-.75-1.994-1.802-2.169a47.865 47.865 0 0 0-1.134-.175 2.31 2.31 0 0 1-1.64-1.055l-.822-1.316a2.192 2.192 0 0 0-1.736-1.039 48.774 48.774 0 0 0-5.232 0 2.192 2.192 0 0 0-1.736 1.039l-.821 1.316Z"
            />
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M16.5 12.75a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0ZM18.75 10.5h.008v.008h-.008V10.5Z"
            />
          </svg>
          Change Picture
        </Button>
      )}

      <input
        type="file"
        accept="image/*"
        className="hidden"
        ref={fileInputRef}
        onChange={handleImageChange}
      />

      {!profilePic && (
        <p className="text-sm text-gray-500">
          No photo yet — add one whenever you like.
        </p>
      )}

      {isPreviewOpen && (
        <div
          className="fixed inset-0 z-[1000] bg-black/95 flex items-center justify-center animate-fade-in backdrop-blur-sm"
          onClick={() => setIsPreviewOpen(false)}
        >
          <div className="relative max-w-[80vw] max-h-[75vh] bg-black rounded-2xl border border-zinc-800 shadow-2xl flex flex-col" onClick={(e) => e.stopPropagation()}>
            {/* Header Bar for Buttons */}
            <div className="flex justify-between items-center p-3 border-b border-zinc-800 bg-black rounded-t-2xl">
                {/* Delete Button */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDeleteImage();
                    setIsPreviewOpen(false);
                  }}
                  className="text-red-500 hover:text-red-400 hover:bg-zinc-800 transition-colors p-2 rounded-full"
                  aria-label="Delete Profile Picture"
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill="currentColor"
                    className="size-6"
                  >
                    <path
                      fillRule="evenodd"
                      d="M16.5 4.478v.227a48.816 48.816 0 0 1 3.878.512.75.75 0 1 1-.256 1.478l-.209-.035-1.005 13.07a3 3 0 0 1-2.991 2.77H8.084a3 3 0 0 1-2.991-2.77L4.087 6.66l-.209.035a.75.75 0 0 1-.256-1.478A48.567 48.567 0 0 1 7.5 4.705v-.227c0-1.564 1.213-2.9 2.816-2.951a52.662 52.662 0 0 1 3.369 0c1.603.051 2.815 1.387 2.815 2.951Zm-6.136-1.452a51.196 51.196 0 0 1 3.273 0C14.39 3.05 15 3.684 15 4.478v.113a49.488 49.488 0 0 0-6 0v-.113c0-.794.609-1.428 1.364-1.452Zm-.355 5.945a.75.75 0 1 0-1.5.058l.347 9a.75.75 0 1 0 1.499-.058l-.346-9Zm5.48.058a.75.75 0 1 0-1.498-.058l-.347 9a.75.75 0 0 0 1.5.058l.345-9Z"
                      clipRule="evenodd"
                    />
                  </svg>
                </button>

                {/* Close Button */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setIsPreviewOpen(false);
                  }}
                  className="text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors p-2 rounded-full"
                  aria-label="Close Preview"
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                    strokeWidth={2}
                    stroke="currentColor"
                    className="size-6"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M6 18 18 6M6 6l12 12"
                    />
                  </svg>
                </button>
            </div>

            <div className="p-4 flex-1 flex items-center justify-center overflow-hidden">
                <img
                  src={profilePic}
                  alt="Full Preview"
                  className="max-w-full max-h-[65vh] object-contain rounded-lg shadow-xl"
                />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
