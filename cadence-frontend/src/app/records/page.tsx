import RecordsClient from "@/components/records/RecordsClient";
import { Suspense } from "react";

export default function Page() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-black text-white flex items-center justify-center">Loading records...</div>}>
      <RecordsClient />
    </Suspense>
  );
}
