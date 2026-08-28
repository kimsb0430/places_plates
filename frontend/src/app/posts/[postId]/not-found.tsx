import { EmptyState } from '@/shared/ui/empty-state';

export default function PublicPostNotFound() {
  return (
    <div className="public-page">
      <EmptyState
        eyebrow="STORY NOT FOUND"
        title="공개된 기록을 찾을 수 없습니다."
        description="주소가 잘못되었거나 현재 전체 공개 상태가 아닌 기록입니다."
        actionHref="/posts"
        actionLabel="공개 기록 둘러보기"
        tone="quiet"
      />
    </div>
  );
}
