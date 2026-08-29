import Link from 'next/link';
import { getPublicPhotoUrl } from '../api/public-post-api';
import type {
  DestinationDetail,
  PublicPostDetail as PublicPostDetailData,
  RestaurantDetail,
  RestaurantPriceRange,
  RevisitIntention,
} from '../types';
import { ProtectedPublicImage } from './protected-public-image';

interface PublicPostDetailProps {
  post: PublicPostDetailData;
}

export function PublicPostDetail({ post }: PublicPostDetailProps) {
  const primaryPhoto = post.photos.find((photo) => photo.cover) ?? post.photos[0];
  const secondaryPhotos = post.photos.filter((photo) => photo.id !== primaryPhoto?.id);
  const visitDate = `${post.publicVisitYear}-${String(post.publicVisitMonth).padStart(2, '0')}`;

  return (
    <article className={`public-detail is-${post.category.toLowerCase()}`}>
      <Link className="public-detail-back" href="/posts">← 공개 기록으로 돌아가기</Link>

      <header className="public-detail-header">
        <div>
          <p className="overline">
            {post.category === 'RESTAURANT' ? 'PLATE · RESTAURANT' : 'PLACE · DESTINATION'}
          </p>
          <h1>{post.title}</h1>
          <p className="public-detail-lead">
            {post.summary ?? '이 기록의 한줄평은 아직 준비 중입니다.'}
          </p>
        </div>
        <dl className="public-detail-identity">
          <div>
            <dt>방문 시기</dt>
            <dd><time dateTime={visitDate}>{post.publicVisitYear}년 {post.publicVisitMonth}월</time></dd>
          </div>
          <div>
            <dt>장소</dt>
            <dd>{post.place?.name ?? '장소 정보 준비 중'}</dd>
          </div>
        </dl>
      </header>

      <section className="public-detail-gallery" aria-label={`${post.title} 사진`}>
        {primaryPhoto ? (
          <ProtectedPhoto
            className="public-detail-primary-photo"
            photo={primaryPhoto}
            sizes="(max-width: 980px) 100vw, 72vw"
            priority
          />
        ) : (
          <div className="public-detail-photo-placeholder" aria-label="표시할 공개 사진이 없습니다.">
            <span>{post.category === 'RESTAURANT' ? 'PLATE' : 'PLACE'}</span>
          </div>
        )}
        {secondaryPhotos.length > 0 && (
          <div className="public-detail-secondary-photos">
            {secondaryPhotos.map((photo) => (
              <ProtectedPhoto
                key={photo.id}
                className="public-detail-secondary-photo"
                photo={photo}
                sizes="(max-width: 640px) 100vw, (max-width: 980px) 50vw, 33vw"
              />
            ))}
          </div>
        )}
      </section>

      <div className="public-detail-reading-grid">
        <section className="public-detail-story" aria-labelledby="public-detail-story-heading">
          <p className="overline">PERSONAL NOTE</p>
          <h2 id="public-detail-story-heading">그곳에서 남긴 기록</h2>
          <p>{post.content ?? '아직 긴 기록은 작성되지 않았습니다.'}</p>
        </section>

        <aside className="public-detail-facts" aria-label="카테고리별 상세 정보">
          {post.category === 'RESTAURANT' ? (
            <RestaurantFacts details={post.restaurantDetails} />
          ) : (
            <DestinationFacts details={post.destinationDetails} />
          )}
          {post.place && (
            <Link href={`/posts/${post.id}/place`}>
              이 장소의 방문 기록 보기 <span aria-hidden="true">→</span>
            </Link>
          )}
          {post.place?.googleMapsUrl && (
            <a href={post.place.googleMapsUrl} target="_blank" rel="noreferrer">
              Google 지도에서 장소 보기 <span aria-hidden="true">↗</span>
            </a>
          )}
        </aside>
      </div>

      <p className="public-detail-protection-note">
        공개 사진은 촬영 메타데이터를 제거하고 Places &amp; Plates 워터마크를 적용한 파생본입니다.
      </p>
    </article>
  );
}

interface ProtectedPhotoProps {
  className: string;
  photo: PublicPostDetailData['photos'][number];
  sizes: string;
  priority?: boolean;
}

function ProtectedPhoto({ className, photo, sizes, priority = false }: ProtectedPhotoProps) {
  return (
    <figure className={className}>
      <ProtectedPublicImage
        src={getPublicPhotoUrl(photo.path)}
        alt={photo.altText}
        sizes={sizes}
        priority={priority}
        shieldClassName="public-detail-photo-shield"
      />
    </figure>
  );
}

function RestaurantFacts({ details }: { details: RestaurantDetail | null }) {
  const facts = details ? [
    ['평점', details.rating == null ? null : `${details.rating.toFixed(1)} / 5`],
    ['추천 메뉴', details.recommendedMenu],
    ['가격대', priceRangeLabel(details.priceRange)],
    ['대기 시간', details.waitingMinutes == null ? null : `${details.waitingMinutes}분`],
    ['재방문', revisitLabel(details.revisitIntention)],
  ] : [];
  return <FactList title="맛집 기록" facts={facts} />;
}

function DestinationFacts({ details }: { details: DestinationDetail | null }) {
  const facts = details ? [
    ['추천 시간', details.recommendedTime],
    ['소요 시간', details.durationMinutes == null ? null : formatDuration(details.durationMinutes)],
    ['볼거리', details.highlights],
    ['여행 팁', details.travelTips],
  ] : [];
  return <FactList title="여행지 기록" facts={facts} />;
}

function FactList({ title, facts }: { title: string; facts: (string | null)[][] }) {
  const visibleFacts = facts.filter(([, value]) => value);
  return (
    <div>
      <p className="overline">CATEGORY NOTE</p>
      <h2>{title}</h2>
      {visibleFacts.length > 0 ? (
        <dl>
          {visibleFacts.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="public-detail-facts-empty">추가로 공개된 상세 정보가 없습니다.</p>
      )}
    </div>
  );
}

function priceRangeLabel(value: RestaurantPriceRange | null): string | null {
  if (value === 'BUDGET') return '가볍게';
  if (value === 'MODERATE') return '보통';
  if (value === 'EXPENSIVE') return '특별한 날';
  if (value === 'LUXURY') return '고급';
  return null;
}

function revisitLabel(value: RevisitIntention | null): string | null {
  if (value === 'YES') return '다시 가고 싶어요';
  if (value === 'MAYBE') return '기회가 된다면';
  if (value === 'NO') return '한 번의 경험으로 충분해요';
  return null;
}

function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (hours === 0) return `${remainingMinutes}분`;
  if (remainingMinutes === 0) return `${hours}시간`;
  return `${hours}시간 ${remainingMinutes}분`;
}
