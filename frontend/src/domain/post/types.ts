import type { PostCategory } from '@/domain/photo/types';
export type { PostCategory } from '@/domain/photo/types';

export type RestaurantPriceRange = 'BUDGET' | 'MODERATE' | 'EXPENSIVE' | 'LUXURY';
export type RevisitIntention = 'YES' | 'MAYBE' | 'NO';
export type PostVisibility = 'PRIVATE' | 'UNLISTED' | 'PUBLIC';
export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface RestaurantDetail {
  rating: number | null;
  recommendedMenu: string | null;
  priceRange: RestaurantPriceRange | null;
  waitingMinutes: number | null;
  revisitIntention: RevisitIntention | null;
}

export interface DestinationDetail {
  recommendedTime: string | null;
  durationMinutes: number | null;
  highlights: string | null;
  travelTips: string | null;
}

export interface Place {
  id: string;
  source: 'GOOGLE' | 'MANUAL';
  googlePlaceId: string | null;
  name: string;
  placeType: string | null;
  formattedAddress: string | null;
  latitude: number | null;
  longitude: number | null;
  googleMapsUrl: string | null;
  refreshedAt: string | null;
}

export interface PlaceSearchResult {
  googlePlaceId: string;
  name: string;
  placeType: string | null;
  formattedAddress: string | null;
  latitude: number | null;
  longitude: number | null;
  googleMapsUrl: string | null;
}

export interface PlaceConnectionInput {
  source: 'GOOGLE' | 'MANUAL';
  googlePlaceId: string | null;
  name: string;
  placeType: string | null;
  formattedAddress: string | null;
  latitude: number | null;
  longitude: number | null;
}

export interface DraftPost {
  id: string;
  category: PostCategory;
  title: string;
  summary: string | null;
  content: string | null;
  publicVisitYear: number | null;
  publicVisitMonth: number | null;
  place: Place | null;
  restaurantDetails: RestaurantDetail | null;
  destinationDetails: DestinationDetail | null;
  visibility: PostVisibility;
  status: PostStatus;
  createdAt: string;
  updatedAt: string;
}

export interface DraftPostUpdateInput {
  title: string;
  summary: string | null;
  content: string | null;
  publicVisitYear: number | null;
  publicVisitMonth: number | null;
  restaurantDetails: RestaurantDetail | null;
  destinationDetails: DestinationDetail | null;
}

export interface PostPublicationCheck {
  code: string;
  label: string;
  passed: boolean;
}

export interface PostPublicationReadiness {
  ready: boolean;
  checks: PostPublicationCheck[];
}

export interface PostPublicationResult {
  id: string;
  visibility: PostVisibility;
  status: 'PUBLISHED';
  publishedAt: string;
}

export type PublicPostCategoryFilter = 'ALL' | PostCategory;
export type PublicPostSort = 'LATEST' | 'OLDEST';

export interface PublicPostCounts {
  all: number;
  restaurant: number;
  destination: number;
}

export interface PublicPostSummary {
  id: string;
  category: PostCategory;
  title: string;
  summary: string | null;
  publicVisitYear: number;
  publicVisitMonth: number;
  publishedAt: string;
  cover: PublicPostCover | null;
}

export interface PublicPostCover {
  path: string;
  altText: string;
  width: number;
  height: number;
}

export interface PublicPostPhoto extends PublicPostCover {
  id: string;
  cover: boolean;
}

export interface PublicPostPlace {
  name: string;
  googleMapsUrl: string | null;
}

export interface PublicPostDetail {
  id: string;
  category: PostCategory;
  title: string;
  summary: string | null;
  content: string | null;
  publicVisitYear: number;
  publicVisitMonth: number;
  place: PublicPostPlace | null;
  restaurantDetails: RestaurantDetail | null;
  destinationDetails: DestinationDetail | null;
  photos: PublicPostPhoto[];
}

export interface PublicPostList {
  counts: PublicPostCounts;
  posts: PublicPostSummary[];
}
