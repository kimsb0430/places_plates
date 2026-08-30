import Link from 'next/link';
import { getPublicPosts, PublicPostApiError } from '@/domain/post/api/public-post-api';
import { PublicPostIndex } from '@/domain/post/components/public-post-index';
import type { PublicPostList } from '@/domain/post/types';
import { EmptyState } from '@/shared/ui/empty-state';

export const dynamic = 'force-dynamic';

export default async function Home() {
  let archive: PublicPostList;

  try {
    archive = await getPublicPosts(undefined, 'LATEST');
  } catch (error: unknown) {
    return <UnavailableHome message={error instanceof PublicPostApiError ? error.message : undefined} />;
  }

  return (
    <>
      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="overline">MY TRAVEL &amp; DINING ARCHIVE</p>
          <h1>나의 기록</h1>
          <p className="hero-description">여행에서 만난 풍경과 한 끼를 사진, 지도 그리고 그날의 문장으로 남깁니다.</p>
          <div className="hero-stats" aria-label="공개 기록 통계">
            <span><b>{archive.counts.all}</b>records</span>
            <span><b>{archive.counts.restaurant}</b>plates</span>
            <span><b>{archive.counts.destination}</b>places</span>
          </div>
        </div>
        <Link className="hero-write-card" href="/manage">기록하기</Link>
      </section>

      <section className="archive home-public-archive" id="archive">
        <div className="archive-heading">
          <div><p className="overline">LATEST PUBLIC RECORDS</p><h2>최근 공개 기록</h2></div>
          <div className="home-archive-links">
            <Link href="/posts">전체 기록 보기 <span aria-hidden="true">→</span></Link>
            <Link href="/map">지도에서 한눈에 보기 <span aria-hidden="true">↗</span></Link>
          </div>
        </div>
        {archive.posts.length > 0 ? (
          <PublicPostIndex posts={archive.posts.slice(0, 6)} />
        ) : (
          <EmptyState
            eyebrow="NO PUBLIC POSTS YET"
            title="아직 공개된 기록이 없습니다."
            description="관리 공간에서 기록을 전체 공개하면 실제 사진과 내용이 이곳에 표시됩니다."
            actionHref="/manage"
            actionLabel="기록 작성하러 가기"
            tone="quiet"
          />
        )}
      </section>
    </>
  );
}

function UnavailableHome({ message }: { message?: string }) {
  return (
    <section className="archive home-public-archive">
      <EmptyState
        eyebrow="ARCHIVE UNAVAILABLE"
        title="공개 기록을 잠시 불러오지 못했습니다."
        description={message ?? '잠시 후 다시 시도해주세요.'}
        actionHref="/"
        actionLabel="다시 불러오기"
        tone="quiet"
      />
    </section>
  );
}
