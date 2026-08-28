import { EmptyState } from '@/shared/ui/empty-state';

export default function PublicPlaceHistoryNotFound() {
  return (
    <div className="public-page">
      <EmptyState
        eyebrow="PLACE HISTORY NOT FOUND"
        title="공개된 장소 방문 기록을 찾을 수 없습니다."
        description="주소가 잘못되었거나 진입한 기록이 현재 전체 공개 상태가 아닙니다."
        actionHref="/posts"
        actionLabel="공개 기록 둘러보기"
        tone="quiet"
      />
    </div>
  );
}
