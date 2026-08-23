'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const navigationItems = [
  { href: '/posts', label: '기록', path: '/posts' },
  { href: '/map', label: '지도', path: '/map' },
  { href: '/#journeys', label: '여행', path: null },
] as const;

export function HeaderNavigation() {
  const pathname = usePathname();

  return (
    <>
      <nav className="main-nav" aria-label="주요 메뉴">
        {navigationItems.map((item) => {
          const isCurrent = item.path === pathname;

          return (
            <Link
              className={isCurrent ? 'active' : undefined}
              href={item.href}
              aria-current={isCurrent ? 'page' : undefined}
              key={item.href}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
      <Link
        className={`account-link ${pathname === '/login' ? 'active' : ''}`}
        href="/login"
        aria-current={pathname === '/login' ? 'page' : undefined}
      >
        로그인
      </Link>
    </>
  );
}
