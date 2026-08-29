'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
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
  const searchParameters = useSearchParams();

  return (
    <nav className="map-category-tabs" aria-label="지도 기록 카테고리">
      <div>
        {TABS.map((tab) => {
          const isSelected = tab.category === selected;
          const href = createCategoryHref(tab.category, searchParameters);
          return (
            <Link
              key={tab.category}
              href={href}
              aria-current={isSelected ? 'page' : undefined}
              aria-label={`${tab.label} 지도 기록 ${counts[tab.countKey]}개`}
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

function createCategoryHref(
  category: MapCategoryFilter,
  currentParameters: ReturnType<typeof useSearchParams>,
): string {
  const parameters = new URLSearchParams(currentParameters.toString());
  if (category === 'ALL') parameters.delete('category');
  else parameters.set('category', category);
  const query = parameters.toString();
  return query ? `/map?${query}` : '/map';
}
