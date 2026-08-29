import type { MapPostMarker } from './types';

export type MapClusterCategory = MapPostMarker['category'] | 'MIXED';

export interface MapClusterPresentation {
  count: number;
  category: MapClusterCategory;
  color: string;
  label: string;
}

export function createMapClusterPresentation(
  categories: readonly MapPostMarker['category'][],
): MapClusterPresentation {
  const category = resolveClusterCategory(categories);
  const count = categories.length;
  return {
    count,
    category,
    color: clusterColor(category),
    label: `${count}개의 공개 기록. 누르면 확대됩니다.`,
  };
}

function resolveClusterCategory(
  categories: readonly MapPostMarker['category'][],
): MapClusterCategory {
  const uniqueCategories = new Set(categories);
  if (uniqueCategories.size !== 1) return 'MIXED';
  return uniqueCategories.values().next().value ?? 'MIXED';
}

function clusterColor(category: MapClusterCategory): string {
  if (category === 'RESTAURANT') return '#c24c20';
  if (category === 'DESTINATION') return '#477f6c';
  return '#17342d';
}
