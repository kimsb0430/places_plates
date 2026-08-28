import type { MapPostMarker } from './types';

export interface MapViewportBounds {
  north: number;
  east: number;
  south: number;
  west: number;
}

export interface MapViewportPostCounts {
  total: number;
  restaurant: number;
  destination: number;
}

export function countPostsWithinMapBounds(
  posts: MapPostMarker[],
  bounds: MapViewportBounds,
): MapViewportPostCounts {
  return posts.reduce<MapViewportPostCounts>((counts, post) => {
    if (!isWithinMapBounds(post.latitude, post.longitude, bounds)) return counts;
    counts.total += 1;
    if (post.category === 'RESTAURANT') counts.restaurant += 1;
    else counts.destination += 1;
    return counts;
  }, { total: 0, restaurant: 0, destination: 0 });
}

function isWithinMapBounds(
  latitude: number,
  longitude: number,
  bounds: MapViewportBounds,
): boolean {
  if (latitude < bounds.south || latitude > bounds.north) return false;
  if (bounds.west <= bounds.east) {
    return longitude >= bounds.west && longitude <= bounds.east;
  }
  return longitude >= bounds.west || longitude <= bounds.east;
}
