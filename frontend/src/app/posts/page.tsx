import type { Metadata } from 'next';
import { EmptyState } from '@/shared/ui/empty-state';

export const metadata: Metadata = {
  title: '기록 | Places & Plates',
  description: '공개된 맛집과 여행지 기록을 둘러봅니다.',
};

export default function PostsPage() {
  return (
    <div className="public-page">
      <header className="public-page-heading">
        <div>
          <p className="overline">PUBLIC ARCHIVE</p>
          <h1>공개 기록</h1>
        </div>
        <p>맛집과 여행지 기록을 한 장면씩 읽는 공간입니다.</p>
      </header>
      <EmptyState
        eyebrow="NO PUBLIC POSTS YET"
        title="아직 공개된 기록이 없습니다."
        description="첫 기록이 공개되면 이곳에서 맛집과 여행지를 카테고리별로 볼 수 있습니다. 지금은 홈의 기록 미리보기를 둘러보세요."
        actionHref="/#archive"
        actionLabel="기록 미리보기 보기"
      />
    </div>
  );
}
