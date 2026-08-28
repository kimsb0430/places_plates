import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { cache } from 'react';
import { getPublicPost, PublicPostApiError } from '@/domain/post/api/public-post-api';
import { PublicPostDetail } from '@/domain/post/components/public-post-detail';
import type { PublicPostDetail as PublicPostDetailData } from '@/domain/post/types';
import { EmptyState } from '@/shared/ui/empty-state';

export const dynamic = 'force-dynamic';

interface PublicPostDetailPageProps {
  params: Promise<{ postId: string }>;
}

const loadPublicPost = cache(getPublicPost);

export async function generateMetadata({ params }: PublicPostDetailPageProps): Promise<Metadata> {
  const { postId } = await params;
  if (!isUuid(postId)) return { title: '기록을 찾을 수 없습니다 | Places & Plates' };
  try {
    const post = await loadPublicPost(postId);
    return {
      title: `${post.title} | Places & Plates`,
      description: post.summary ?? `${post.publicVisitYear}년 ${post.publicVisitMonth}월의 공개 기록`,
    };
  } catch {
    return { title: '공개 기록 | Places & Plates' };
  }
}

export default async function PublicPostDetailPage({ params }: PublicPostDetailPageProps) {
  const { postId } = await params;
  if (!isUuid(postId)) notFound();

  let post: PublicPostDetailData | null = null;
  let loadError: unknown = null;
  try {
    post = await loadPublicPost(postId);
  } catch (error: unknown) {
    loadError = error;
  }

  if (post) return <PublicPostDetail post={post} />;
  if (loadError instanceof PublicPostApiError && loadError.status === 404) notFound();
  const message = loadError instanceof PublicPostApiError
    ? loadError.message
    : '공개 기록을 불러오는 중 문제가 발생했습니다.';
  return (
    <div className="public-page">
      <EmptyState
        eyebrow="DETAIL UNAVAILABLE"
        title="이 기록을 잠시 불러오지 못했습니다."
        description={message}
        actionHref={`/posts/${postId}`}
        actionLabel="다시 불러오기"
        tone="quiet"
      />
    </div>
  );
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
