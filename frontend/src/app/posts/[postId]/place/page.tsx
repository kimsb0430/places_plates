import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { cache } from 'react';
import { getPublicPlaceHistory, PublicPostApiError } from '@/domain/post/api/public-post-api';
import { PublicPlaceHistory } from '@/domain/post/components/public-place-history';
import type { PublicPlaceHistory as PublicPlaceHistoryData } from '@/domain/post/types';
import { EmptyState } from '@/shared/ui/empty-state';

export const dynamic = 'force-dynamic';

interface PublicPlaceHistoryPageProps {
  params: Promise<{ postId: string }>;
}

const loadPublicPlaceHistory = cache(getPublicPlaceHistory);

export async function generateMetadata({ params }: PublicPlaceHistoryPageProps): Promise<Metadata> {
  const { postId } = await params;
  if (!isUuid(postId)) return { title: '장소를 찾을 수 없습니다 | Places & Plates' };
  try {
    const history = await loadPublicPlaceHistory(postId);
    return {
      title: `${history.place.name} 방문 기록 | Places & Plates`,
      description: `${history.place.name}에 남긴 공개 방문 기록 ${history.visitCount}개`,
    };
  } catch {
    return { title: '장소 방문 기록 | Places & Plates' };
  }
}

export default async function PublicPlaceHistoryPage({ params }: PublicPlaceHistoryPageProps) {
  const { postId } = await params;
  if (!isUuid(postId)) notFound();

  let history: PublicPlaceHistoryData | null = null;
  let loadError: unknown = null;
  try {
    history = await loadPublicPlaceHistory(postId);
  } catch (error: unknown) {
    loadError = error;
  }

  if (history) return <PublicPlaceHistory anchorPostId={postId} history={history} />;
  if (loadError instanceof PublicPostApiError && loadError.status === 404) notFound();
  const message = loadError instanceof PublicPostApiError
    ? loadError.message
    : '공개 장소 방문 기록을 불러오는 중 문제가 발생했습니다.';
  return (
    <div className="public-page">
      <EmptyState
        eyebrow="PLACE HISTORY UNAVAILABLE"
        title="이 장소의 기록을 잠시 불러오지 못했습니다."
        description={message}
        actionHref={`/posts/${postId}/place`}
        actionLabel="다시 불러오기"
        tone="quiet"
      />
    </div>
  );
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
