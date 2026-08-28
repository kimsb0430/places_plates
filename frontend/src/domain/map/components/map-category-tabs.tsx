import Link from 'next/link';
import type { MapCategoryFilter, MapPostCounts } from '../types';

interface MapCategoryTabsProps {
  selected: MapCategoryFilter;
  counts: MapPostCounts;
}

const TABS: Array<{
  category: MapCategoryFilter;
  label: string;
  countKey: keyof MapPostCounts;
  tone?: 'food' | 'travel';
}> = [
  { category: 'ALL', label: '전체', countKey: 'all' },
  { category: 'RESTAURANT', label: '맛집', countKey: 'restaurant', tone: 'food' },
  { category: 'DESTINATION', label: '여행지', countKey: 'destination', tone: 'travel' },
];

export function MapCategoryTabs({ selected, counts }: MapCategoryTabsProps) {
  return (
    <nav className="map-category-tabs" aria-label="지도 기록 카테고리">
      <div role="tablist">
        {TABS.map((tab) => {
          const isSelected = tab.category === selected;
          const href = tab.category === 'ALL' ? '/map' : `/map?category=${tab.category}`;
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
