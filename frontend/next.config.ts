import type { NextConfig } from 'next';

const publicApiUrl = new URL(
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || 'http://localhost:8080',
);

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        source: '/_next/image',
        headers: [
          { key: 'Cross-Origin-Resource-Policy', value: 'same-origin' },
          { key: 'Content-Security-Policy', value: "default-src 'none'; frame-ancestors 'none'; sandbox" },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
        ],
      },
    ];
  },
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
