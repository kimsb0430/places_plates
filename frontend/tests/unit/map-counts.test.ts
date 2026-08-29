import assert from 'node:assert/strict';
import test from 'node:test';
import { createMapClusterPresentation } from '../../src/domain/map/map-cluster-presentation';
import {
  countPostsWithinMapBounds,
  getPostsWithinMapBounds,
} from '../../src/domain/map/map-viewport-count';
import type { MapPostMarker } from '../../src/domain/map/types';

const posts: MapPostMarker[] = [
  mapPost('restaurant-seoul', 'RESTAURANT', 37.5665, 126.978),
  mapPost('destination-seoul', 'DESTINATION', 37.57, 126.99),
  mapPost('restaurant-tokyo', 'RESTAURANT', 35.6812, 139.7671),
];

test('현재 영역 합계는 경계 안의 개별 게시물을 카테고리별로 센다', () => {
  const bounds = { north: 37.57, east: 126.99, south: 37.56, west: 126.97 };

  assert.deepEqual(countPostsWithinMapBounds(posts, bounds), {
    total: 2,
    restaurant: 1,
    destination: 1,
  });
  assert.deepEqual(
    getPostsWithinMapBounds(posts, bounds).map((post) => post.id),
    ['restaurant-seoul', 'destination-seoul'],
  );
});

test('날짜 변경선을 가로지르는 영역도 동쪽과 서쪽 게시물을 모두 센다', () => {
  const dateLinePosts = [
    mapPost('east', 'DESTINATION', 0, 179.5),
    mapPost('west', 'RESTAURANT', 0, -179.5),
    mapPost('outside', 'RESTAURANT', 0, 170),
  ];

  assert.deepEqual(countPostsWithinMapBounds(dateLinePosts, {
    north: 10,
    east: -175,
    south: -10,
    west: 175,
  }), {
    total: 2,
    restaurant: 1,
    destination: 1,
  });
});

test('클러스터 숫자는 장소 수가 아니라 포함된 게시물 수와 일치한다', () => {
  const restaurantCluster = createMapClusterPresentation(['RESTAURANT', 'RESTAURANT']);
  const mixedCluster = createMapClusterPresentation(['RESTAURANT', 'DESTINATION', 'RESTAURANT']);

  assert.deepEqual(restaurantCluster, {
    count: 2,
    category: 'RESTAURANT',
    color: '#c24c20',
    label: '2개의 공개 기록. 누르면 확대됩니다.',
  });
  assert.deepEqual(mixedCluster, {
    count: 3,
    category: 'MIXED',
    color: '#17342d',
    label: '3개의 공개 기록. 누르면 확대됩니다.',
  });
});

function mapPost(
  id: string,
  category: MapPostMarker['category'],
  latitude: number,
  longitude: number,
): MapPostMarker {
  return {
    id,
    category,
    title: `${id} 기록`,
    placeName: `${id} 장소`,
    latitude,
    longitude,
    publicVisitYear: 2026,
    publicVisitMonth: 8,
  };
}
