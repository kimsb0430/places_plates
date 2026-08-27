import type { PostCategory } from '@/domain/photo/types';

export type RestaurantPriceRange = 'BUDGET' | 'MODERATE' | 'EXPENSIVE' | 'LUXURY';
export type RevisitIntention = 'YES' | 'MAYBE' | 'NO';

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

export interface DraftPost {
  id: string;
  category: PostCategory;
  title: string;
  summary: string | null;
  content: string | null;
  publicVisitYear: number | null;
  publicVisitMonth: number | null;
  restaurantDetails: RestaurantDetail | null;
  destinationDetails: DestinationDetail | null;
  visibility: 'PRIVATE';
  status: 'DRAFT';
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
