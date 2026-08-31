import type { Metadata } from 'next';
import { PublishedPostView } from '@/domain/post/components/draft-post-view';

export const metadata: Metadata = {
  title: '공개 기록 수정 | Places & Plates',
  description: '게시한 기록의 내용과 사진 구성을 수정하는 관리자 화면입니다.',
};

interface PublishedPostEditPageProps {
  params: Promise<{ postId: string }>;
}

export default async function PublishedPostEditPage({ params }: PublishedPostEditPageProps) {
  const { postId } = await params;

  return (
    <div className="manage-page">
      <PublishedPostView postId={postId} />
    </div>
  );
}
