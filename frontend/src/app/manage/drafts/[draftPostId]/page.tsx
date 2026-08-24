import type { Metadata } from 'next';
import { DraftPostView } from '@/domain/post/components/draft-post-view';

export const metadata: Metadata = {
  title: '비공개 초안 | Places & Plates',
  description: '업로드한 사진과 연결된 비공개 기록 초안입니다.',
};

interface DraftPostPageProps {
  params: Promise<{ draftPostId: string }>;
}

export default async function DraftPostPage({ params }: DraftPostPageProps) {
  const { draftPostId } = await params;

  return (
    <div className="manage-page">
      <DraftPostView draftPostId={draftPostId} />
    </div>
  );
}
