import type { ReactNode } from 'react';
import Link from 'next/link';

interface ApplicationShellProps {
  children: ReactNode;
}

const navigationItems = [
  { href: '/#archive', label: '기록', isCurrent: true },
  { href: '/#map-panel', label: '지도', isCurrent: false },
  { href: '/#journeys', label: '여행', isCurrent: false },
] as const;

export function ApplicationShell({ children }: ApplicationShellProps) {
  return (
    <div className="site-shell">
      <a className="skip-link" href="#main-content">
        본문으로 바로가기
      </a>
      <header className="topbar">
        <Link className="brand" href="/#top" aria-label="Places and Plates 홈">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span>Places <i>&amp;</i> Plates</span>
        </Link>
        <nav className="main-nav" aria-label="주요 메뉴">
          {navigationItems.map((item) => (
            <Link
              className={item.isCurrent ? 'active' : undefined}
              href={item.href}
              aria-current={item.isCurrent ? 'page' : undefined}
              key={item.href}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <button className="round-button" type="button" aria-label="검색">
          ⌕
        </button>
      </header>
      <main className="app-main" id="main-content" tabIndex={-1}>
        {children}
      </main>
      <footer className="site-footer">
        <span>Places &amp; Plates</span>
        <span>사진과 기억이 머무는 개인 여행 아카이브</span>
        <span>© 2026</span>
      </footer>
    </div>
  );
}
