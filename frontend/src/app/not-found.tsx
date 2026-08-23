import { EmptyState } from '@/shared/ui/empty-state';

export default function NotFoundPage() {
  return (
    <div className="public-page not-found-page">
      <EmptyState
        eyebrow="PAGE NOT FOUND"
        title="요청한 페이지를 찾을 수 없습니다."
        description="주소가 바뀌었거나 삭제된 페이지입니다. 홈에서 기록과 지도를 다시 둘러볼 수 있습니다."
        actionHref="/"
        actionLabel="홈으로 돌아가기"
        tone="quiet"
      />
    </div>
  );
}
