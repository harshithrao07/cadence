"use client";

import { useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AuthenticationResponseDTO } from "@/types/Auth";
import { toast } from "sonner";

function OAuthSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const token = searchParams.get("token");
    const userId = searchParams.get("userId");
    const refreshToken = searchParams.get("refresh");

    if (token && userId && refreshToken) {
      const authDetails: AuthenticationResponseDTO = {
        id: userId,
        accessToken: token,
        refreshToken: refreshToken,
      };

      localStorage.setItem("auth_details", JSON.stringify(authDetails));
      toast.success("Successfully logged in with Google!");
      router.push("/");
    } else {
      // If params are missing, maybe we just landed here?
      // But we should have them.
      // Let's check if we have them before redirecting to error
      if (!token && !userId && !refreshToken) {
          // Could be initial render or direct access
          // We can wait a bit or redirect
           router.push("/auth/login");
      }
    }
  }, [router, searchParams]);

  return (
    <div className="flex justify-center items-center h-screen bg-black">
      <div className="flex flex-col items-center gap-4">
        <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-red-500"></div>
        <p className="text-white text-lg">Completing login...</p>
      </div>
    </div>
  );
}

export default function OAuthSuccessPage() {
  return (
    <Suspense fallback={<div className="h-screen bg-black flex justify-center items-center text-white">Loading...</div>}>
      <OAuthSuccessContent />
    </Suspense>
  );
}
