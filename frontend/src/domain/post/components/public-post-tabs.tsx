import Link from 'next/link';
import type { PublicPostCategoryFilter, PublicPostCounts, PublicPostSort } from '../types';

interface PublicPostTabsProps {
  selected: PublicPostCategoryFilter;
  counts: PublicPostCounts;
  sort: PublicPostSort;
}

const TABS: Array<{
  category: PublicPostCategoryFilter;
  label: string;
  countKey: keyof PublicPostCounts;
  tone?: 'food' | 'travel';
}> = [
  { category: 'ALL', label: '전체', countKey: 'all' },
  { category: 'RESTAURANT', label: '맛집', countKey: 'restaurant', tone: 'food' },
  { category: 'DESTINATION', label: '여행지', countKey: 'destination', tone: 'travel' },
];

export function PublicPostTabs({ selected, counts, sort }: PublicPostTabsProps) {
  return (
    <nav className="public-post-tabs" aria-label="공개 기록 카테고리">
      <div role="tablist">
        {TABS.map((tab) => {
          const isSelected = selected === tab.category;
          const parameters = new URLSearchParams();
          if (tab.category !== 'ALL') parameters.set('category', tab.category);
          if (sort === 'OLDEST') parameters.set('sort', sort);
          const query = parameters.toString();
          const href = query ? `/posts?${query}` : '/posts';
          return (
            <Link
              key={tab.category}
              href={href}
              role="tab"
              aria-selected={isSelected}
              className={isSelected ? 'is-selected' : undefined}
            >
              {tab.tone && <i className={tab.tone} aria-hidden="true">●</i>}
              {tab.label}
              <span>{counts[tab.countKey]}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
