import type { PublicPostSummary } from '../types';

interface PublicPostIndexProps {
  posts: PublicPostSummary[];
}

export function PublicPostIndex({ posts }: PublicPostIndexProps) {
  return (
    <ol className="public-post-index" aria-label="공개 게시물 목록">
      {posts.map((post, index) => (
        <li key={post.id}>
          <article>
            <span className="public-post-number" aria-hidden="true">
              {String(index + 1).padStart(2, '0')}
            </span>
            <div className="public-post-index-body">
              <p className={`public-post-category is-${post.category.toLowerCase()}`}>
                {post.category === 'RESTAURANT' ? 'PLATE · 맛집' : 'PLACE · 여행지'}
              </p>
              <h2>{post.title}</h2>
              <p>{post.summary ?? '이 기록의 한줄평은 아직 준비 중입니다.'}</p>
            </div>
            <time dateTime={`${post.publicVisitYear}-${String(post.publicVisitMonth).padStart(2, '0')}`}>
              {post.publicVisitYear}년 {post.publicVisitMonth}월
            </time>
          </article>
        </li>
      ))}
    </ol>
  );
}
