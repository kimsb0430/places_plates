import type { PostCategory } from '@/domain/photo/types';

export type MapCategoryFilter = 'ALL' | PostCategory;

export interface MapPostCounts {
  all: number;
  restaurant: number;
  destination: number;
}

export interface MapPostMarker {
  id: string;
  category: PostCategory;
  title: string;
  placeName: string;
  latitude: number;
  longitude: number;
  publicVisitYear: number;
  publicVisitMonth: number;
}

export interface MapPostList {
  counts: MapPostCounts;
  posts: MapPostMarker[];
}

export interface MapViewState {
  latitude: number;
  longitude: number;
  zoom: number;
}
