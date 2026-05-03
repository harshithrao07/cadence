"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { User, LogOut, Search } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { toast } from "sonner";
import GlobalSearch from "./search/GlobalSearch";
import { usePlayer } from "@/context/PlayerContext";
import api from "@/lib/api";

export default function Navbar() {
  const [userId, setUserId] = useState<string | null>(null);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const { resetPlayer } = usePlayer();

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 0);
    };
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  useEffect(() => {
    const authDetails = localStorage.getItem("auth_details");
    if (authDetails) {
      try {
        const { id } = JSON.parse(authDetails);
        setUserId(id);
      } catch (e) {
        console.error("Failed to parse auth details", e);
      }
    }
  }, [pathname]);

  const handleLogout = () => {
    toast("Are you sure you want to logout?", {
      action: {
        label: "Logout",
        onClick: async () => {
          try {
            resetPlayer();

            document.cookie.split(";").forEach((c) => {
              document.cookie = c
                .replace(/^ +/, "")
                .replace(/=.*/, "=;expires=" + new Date().toUTCString() + ";path=/");
            });

            localStorage.removeItem("auth_details");
            setUserId(null);
            router.push("/auth/login");
            toast.success("Logged out successfully");
          } catch (e) {
            console.error("Logout error:", e);
          }
        },
      },
      cancel: {
        label: "Cancel",
        onClick: () => {},
      },
    });
  };

  if (pathname.startsWith("/auth") || pathname === "/records/add") return null;

  return (
    <nav
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        isScrolled
          ? "bg-black/90 backdrop-blur-md border-b border-zinc-800"
          : "bg-transparent border-b border-transparent"
      }`}
    >
      <div className="max-w-6xl mx-auto flex items-center justify-between px-6 py-4">
        <Link
          href="/"
          className="flex items-center gap-2 hover:opacity-80 transition-opacity"
        >
          <Image
            src="/images/cadence-logo.png"
            alt="Cadence Logo"
            width={30}
            height={30}
            className="object-contain brightness-0 invert"
          />
          <span className="text-2xl font-bold tracking-tighter text-white">
            Cadence
          </span>
        </Link>

        {/* Search Bar */}
        <div className="flex-1 max-w-md mx-6 hidden sm:block">
            <div 
                onClick={() => setIsSearchOpen(true)}
                className="relative group cursor-text"
            >
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Search className="h-4 w-4 text-zinc-400 group-hover:text-white transition" />
                </div>
                <div className="w-full bg-zinc-900 border border-zinc-800 text-zinc-400 text-sm rounded-full py-2 pl-10 pr-4 group-hover:bg-zinc-800 group-hover:text-zinc-300 transition">
                    Search songs, artists, records...
                </div>
            </div>
        </div>

        <div className="flex items-center gap-6 text-sm text-zinc-300">
          <Link href="/artists" className="hover:text-white transition">
            Artists
          </Link>
          {userId ? (
            <>
              <Link
                href={`/profile/${userId}`}
                className="hover:text-white transition flex items-center gap-2"
              >
                <User className="w-4 h-4" />
                Profile
              </Link>
              <button
                onClick={handleLogout}
                className="hover:text-white transition flex items-center gap-2"
              >
                <LogOut className="w-4 h-4" />
                Logout
              </button>
            </>
          ) : (
            <Link
              href="/auth/login"
              className="hover:text-white transition flex items-center gap-2"
            >
              <User className="w-4 h-4" />
              Login
            </Link>
          )}
        </div>
      </div>
      <GlobalSearch isOpen={isSearchOpen} onClose={() => setIsSearchOpen(false)} />
    </nav>
  );
}
