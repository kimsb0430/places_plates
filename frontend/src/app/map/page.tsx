import type { Metadata } from 'next';
import { EmptyState } from '@/shared/ui/empty-state';

export const metadata: Metadata = {
  title: '지도 | Places & Plates',
  description: '공개 기록이 있는 장소를 지도에서 둘러봅니다.',
};

export default function MapPage() {
  return (
    <div className="public-page map-page">
      <header className="public-page-heading">
        <div>
          <p className="overline">MAP EXPLORATION</p>
          <h1>기록 지도</h1>
        </div>
        <p>공개된 기록의 위치와 같은 장소의 방문 횟수를 확인합니다.</p>
      </header>
      <div className="map-empty-surface">
        <span className="map-empty-place place-a">KYOTO</span>
        <span className="map-empty-place place-b">OSAKA</span>
        <span className="map-empty-place place-c">SEOUL</span>
        <EmptyState
          eyebrow="NO MAPPED POSTS YET"
          title="지도에 표시할 기록이 없습니다."
          description="장소가 연결된 공개 기록이 생기면 현재 지도 영역의 게시물 수와 위치를 여기에서 확인할 수 있습니다."
          actionHref="/posts"
          actionLabel="공개 기록 확인하기"
          tone="map"
        />
      </div>
    </div>
  );
}
