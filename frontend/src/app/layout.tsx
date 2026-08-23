import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import { ApplicationShell } from '@/shared/ui/application-shell';
import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: 'Places & Plates — 여행과 맛의 기록',
  description: '여행지와 맛집 사진을 지도와 이야기로 남기는 개인 아카이브',
  openGraph: {
    title: 'Places & Plates',
    description: '여행과 맛의 기록',
    type: 'website',
    images: [
      {
        url: '/og.png',
        width: 1678,
        height: 945,
        alt: 'Places & Plates — 여행과 맛의 기록',
      },
    ],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Places & Plates',
    description: '여행과 맛의 기록',
    images: ['/og.png'],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <ApplicationShell>{children}</ApplicationShell>
      </body>
    </html>
  );
}
