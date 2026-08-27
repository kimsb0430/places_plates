import Link from 'next/link';
import type { PublicPostCategoryFilter, PublicPostSort } from '../types';

interface PublicPostSortLinksProps {
  category: PublicPostCategoryFilter;
  selected: PublicPostSort;
}

const SORT_OPTIONS: Array<{ value: PublicPostSort; label: string }> = [
  { value: 'LATEST', label: '최신순' },
  { value: 'OLDEST', label: '오래된순' },
];

export function PublicPostSortLinks({ category, selected }: PublicPostSortLinksProps) {
  return (
    <nav className="public-post-sort" aria-label="공개 기록 정렬">
      {SORT_OPTIONS.map((option) => {
        const parameters = new URLSearchParams();
        if (category !== 'ALL') parameters.set('category', category);
        if (option.value === 'OLDEST') parameters.set('sort', option.value);
        const query = parameters.toString();
        return (
          <Link
            key={option.value}
            href={query ? `/posts?${query}` : '/posts'}
            aria-current={selected === option.value ? 'page' : undefined}
            className={selected === option.value ? 'is-selected' : undefined}
          >
            {option.label}
          </Link>
        );
      })}
    </nav>
  );
}
