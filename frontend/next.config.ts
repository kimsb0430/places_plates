import type { NextConfig } from 'next';

const publicApiUrl = new URL(
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || 'http://localhost:8080',
);

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: publicApiUrl.protocol.replace(':', '') as 'http' | 'https',
        hostname: publicApiUrl.hostname,
        port: publicApiUrl.port,
        pathname: '/api/v1/public/posts/**',
      },
    ],
  },
};

export default nextConfig;
