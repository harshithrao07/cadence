/** @type {import('next').NextConfig} */
const nextConfig = {
  // Build a self-contained server bundle (.next/standalone) so the Docker
  // runtime image can be a thin node:alpine + the standalone output.
  // Without this, the production image needs the full node_modules tree.
  output: "standalone",

  images: {
    domains: [
      "cadencebucket.s3.ap-south-1.amazonaws.com",
      "lh3.googleusercontent.com",
      "cdn.jsdelivr.net",
      "avatars.githubusercontent.com",
      "picsum.photos",
      "cdn-images.dzcdn.net", // Deezer artist/album images (seed fallback)
      "usercontent.jamendo.com", // Jamendo album covers (seed fallback)
    ],
  },
};

export default nextConfig;
