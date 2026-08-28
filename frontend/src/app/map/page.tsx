import type { Metadata } from 'next';
import { getMapPosts, PublicMapApiError } from '@/domain/map/api/public-map-api';
import { GoogleMapExplorer } from '@/domain/map/components/google-map-explorer';
import { MapCategoryTabs } from '@/domain/map/components/map-category-tabs';
import type { MapCategoryFilter, MapPostList } from '@/domain/map/types';
import { EmptyState } from '@/shared/ui/empty-state';

export const metadata: Metadata = {
  title: '지도 | Places & Plates',
  description: '공개 기록이 있는 장소를 카테고리별 Google 지도 마커로 둘러봅니다.',
};

export const dynamic = 'force-dynamic';

interface MapPageProps {
  searchParams: Promise<{ category?: string | string[] }>;
}

export default async function MapPage({ searchParams }: MapPageProps) {
  const parameters = await searchParams;
  const selected = parseCategory(parameters.category);
  let result: MapPostList;

  try {
    result = await getMapPosts(selected === 'ALL' ? undefined : selected);
  } catch (error: unknown) {
    const message = error instanceof PublicMapApiError
      ? error.message
      : '지도 기록을 불러오는 중 문제가 발생했습니다.';
    return <MapUnavailable message={message} />;
  }

  return (
    <div className="public-page map-page">
      <header className="public-page-heading">
        <div>
          <p className="overline">MAP EXPLORATION</p>
          <h1>기록 지도</h1>
        </div>
        <p>맛집은 주황색 ‘맛’, 여행지는 초록색 ‘여’ 마커로 구분합니다.</p>
      </header>

      <div className="map-toolbar">
        <MapCategoryTabs selected={selected} counts={result.counts} />
        <div className="map-marker-legend" aria-label="지도 마커 범례">
          <span><i className="is-restaurant">맛</i> 맛집</span>
          <span><i className="is-destination">여</i> 여행지</span>
        </div>
        <p>전체 공개·게시 완료 상태이며 지도 표시가 허용된 장소만 표시됩니다.</p>
      </div>

      {result.posts.length > 0 ? (
        <GoogleMapExplorer
          apiKey={process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY?.trim() ?? ''}
          mapId={process.env.NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID?.trim() || undefined}
          posts={result.posts}
        />
      ) : (
        <EmptyState
          eyebrow={selected === 'ALL' ? 'NO MAPPED POSTS YET' : 'NO MAPPED POSTS IN THIS CATEGORY'}
          title={selected === 'ALL' ? '지도에 표시할 기록이 없습니다.' : '이 카테고리의 지도 기록이 없습니다.'}
          description="좌표가 연결된 전체 공개 기록이 생기면 카테고리 마커와 숫자에 바로 반영됩니다."
          actionHref={selected === 'ALL' ? '/posts' : '/map'}
          actionLabel={selected === 'ALL' ? '공개 기록 확인하기' : '전체 지도 보기'}
          tone="map"
        />
      )}
    </div>
  );
}

function parseCategory(value?: string | string[]): MapCategoryFilter {
  const category = Array.isArray(value) ? value[0] : value;
  return category === 'RESTAURANT' || category === 'DESTINATION' ? category : 'ALL';
}

function MapUnavailable({ message }: { message: string }) {
  return (
    <div className="public-page map-page">
      <header className="public-page-heading">
        <div>
          <p className="overline">MAP EXPLORATION</p>
          <h1>기록 지도</h1>
        </div>
        <p>공개된 기록의 위치를 카테고리별로 둘러보는 공간입니다.</p>
      </header>
      <EmptyState
        eyebrow="MAP UNAVAILABLE"
        title="지도 기록을 잠시 불러오지 못했습니다."
        description={message}
        actionHref="/map"
        actionLabel="다시 불러오기"
        tone="map"
      />
    </div>
  );
}
