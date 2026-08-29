import type { NextConfig } from 'next';

const DEFAULT_API_BASE_URL = 'http://localhost:8080';

function resolvePublicApiOrigin(value: string | undefined): string {
  try {
    return new URL(value ?? DEFAULT_API_BASE_URL).origin;
  } catch {
    return DEFAULT_API_BASE_URL;
  }
}

export const publicApiOrigin = resolvePublicApiOrigin(process.env.NEXT_PUBLIC_API_BASE_URL);

export const contentSecurityPolicy = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://*.googleapis.com https://*.gstatic.com https://*.google.com https://*.ggpht.com https://*.googleusercontent.com blob:",
  "style-src 'self' 'unsafe-inline' https://*.googleapis.com https://fonts.googleapis.com",
  "img-src 'self' data: blob: https://*.googleapis.com https://*.gstatic.com https://*.google.com https://*.ggpht.com https://*.googleusercontent.com",
  "font-src 'self' data: https://*.gstatic.com https://fonts.gstatic.com",
  `connect-src 'self' ${publicApiOrigin} https://*.supabase.co https://*.googleapis.com https://*.gstatic.com https://*.google.com https://*.ggpht.com https://*.googleusercontent.com data: blob:`,
  'frame-src https://*.google.com',
  'worker-src blob:',
  "object-src 'none'",
  "media-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

export const securityHeaders = [
  { key: 'Content-Security-Policy', value: contentSecurityPolicy },
  { key: 'Cross-Origin-Opener-Policy', value: 'same-origin-allow-popups' },
  {
    key: 'Permissions-Policy',
    value: 'camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()',
  },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'X-Permitted-Cross-Domain-Policies', value: 'none' },
  { key: 'X-XSS-Protection', value: '0' },
];

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        source: '/:path*',
        headers: securityHeaders,
      },
    ];
  },
};

export default nextConfig;
