import type { Metadata } from 'next';
import { getPublicPosts, PublicPostApiError } from '@/domain/post/api/public-post-api';
import { PublicPostIndex } from '@/domain/post/components/public-post-index';
import { PublicPostSortLinks } from '@/domain/post/components/public-post-sort-links';
import { PublicPostTabs } from '@/domain/post/components/public-post-tabs';
import type { PublicPostCategoryFilter, PublicPostList, PublicPostSort } from '@/domain/post/types';
import { EmptyState } from '@/shared/ui/empty-state';

export const metadata: Metadata = {
  title: '기록 | Places & Plates',
  description: '공개된 맛집과 여행지 기록을 둘러봅니다.',
};

export const dynamic = 'force-dynamic';

interface PostsPageProps {
  searchParams: Promise<{ category?: string | string[]; sort?: string | string[] }>;
}

export default async function PostsPage({ searchParams }: PostsPageProps) {
  const parameters = await searchParams;
  const selected = parseCategory(parameters.category);
  const sort = parseSort(parameters.sort);
  const apiCategory = selected === 'ALL' ? undefined : selected;
  let result: PublicPostList;

  try {
    result = await getPublicPosts(apiCategory, sort);
  } catch (error: unknown) {
    const message = error instanceof PublicPostApiError
      ? error.message
      : '공개 기록을 불러오는 중 문제가 발생했습니다.';
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
          eyebrow="ARCHIVE UNAVAILABLE"
          title="공개 기록을 잠시 불러오지 못했습니다."
          description={message}
          actionHref="/posts"
          actionLabel="다시 불러오기"
          tone="quiet"
        />
      </div>
    );
  }

  return (
    <div className="public-page">
      <header className="public-page-heading">
        <div>
          <p className="overline">PUBLIC ARCHIVE</p>
          <h1>공개 기록</h1>
        </div>
        <p>맛집과 여행지 기록을 한 장면씩 읽는 공간입니다.</p>
      </header>

      <div className="public-post-toolbar">
        <PublicPostTabs selected={selected} counts={result.counts} sort={sort} />
        <PublicPostSortLinks category={selected} selected={sort} />
        <p>EXIF를 제거하고 워터마크를 적용한 공개용 사진만 표시됩니다.</p>
      </div>

      {result.posts.length > 0 ? (
        <PublicPostIndex posts={result.posts} />
      ) : (
        <EmptyState
          eyebrow={selected === 'ALL' ? 'NO PUBLIC POSTS YET' : 'NO POSTS IN THIS CATEGORY'}
          title={emptyTitle(selected)}
          description="전체 공개로 게시된 기록이 생기면 이 목록과 카테고리 숫자에 바로 반영됩니다."
          actionHref={selected === 'ALL' ? '/#archive' : '/posts'}
          actionLabel={selected === 'ALL' ? '기록 미리보기 보기' : '전체 기록 보기'}
          tone="quiet"
        />
      )}
    </div>
  );
}

function parseCategory(value?: string | string[]): PublicPostCategoryFilter {
  const category = Array.isArray(value) ? value[0] : value;
  return category === 'RESTAURANT' || category === 'DESTINATION' ? category : 'ALL';
}

function parseSort(value?: string | string[]): PublicPostSort {
  const sort = Array.isArray(value) ? value[0] : value;
  return sort === 'OLDEST' ? 'OLDEST' : 'LATEST';
}

function emptyTitle(category: PublicPostCategoryFilter): string {
  if (category === 'RESTAURANT') return '아직 공개된 맛집 기록이 없습니다.';
  if (category === 'DESTINATION') return '아직 공개된 여행지 기록이 없습니다.';
  return '아직 공개된 기록이 없습니다.';
}
