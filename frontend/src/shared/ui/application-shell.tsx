import type { ReactNode } from 'react';
import Link from 'next/link';
import { HeaderNavigation } from './public-navigation';

interface ApplicationShellProps {
  children: ReactNode;
}

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
        <HeaderNavigation />
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
