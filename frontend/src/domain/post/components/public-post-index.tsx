import Link from 'next/link';
import { getPublicCoverUrl } from '../api/public-post-api';
import { resolvePublicPhotoAltText } from '../public-photo-alt';
import type { PublicPostSummary } from '../types';
import { ProtectedPublicImage } from './protected-public-image';

interface PublicPostIndexProps {
  posts: PublicPostSummary[];
}

export function PublicPostIndex({ posts }: PublicPostIndexProps) {
  return (
    <ol className="public-post-index" aria-label="공개 게시물 카드 목록">
      {posts.map((post, index) => (
        <li key={post.id}>
          <Link className="public-post-card-link" href={`/posts/${post.id}`}>
            <article className="public-post-card">
              <div className="public-post-cover">
                {post.cover ? (
                  <ProtectedPublicImage
                    src={getPublicCoverUrl(post.cover.path)}
                    alt={resolvePublicPhotoAltText(post.cover.altText, `${post.title} 대표 사진`)}
                    sizes="(max-width: 640px) 100vw, (max-width: 980px) 50vw, 33vw"
                    preload={index === 0}
                    shieldClassName="public-post-cover-shield"
                  />
                ) : (
                  <span className="public-post-cover-placeholder" aria-hidden="true">
                    {post.category === 'RESTAURANT' ? 'PLATE' : 'PLACE'}
                  </span>
                )}
                <span className={`public-post-card-badge is-${post.category.toLowerCase()}`}>
                  {post.category === 'RESTAURANT' ? '맛집' : '여행지'}
                </span>
              </div>
              <div className="public-post-index-body">
                <h2>{post.title}</h2>
                <p>{post.summary ?? '이 기록의 한줄평은 아직 준비 중입니다.'}</p>
                <time dateTime={`${post.publicVisitYear}-${String(post.publicVisitMonth).padStart(2, '0')}`}>
                  {post.publicVisitYear}년 {post.publicVisitMonth}월
                </time>
                <span className="public-post-card-action">기록 읽기 <i aria-hidden="true">→</i></span>
              </div>
            </article>
          </Link>
        </li>
      ))}
    </ol>
  );
}
