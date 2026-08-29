import Link from 'next/link';
import { getPublicCoverUrl } from '../api/public-post-api';
import { resolvePublicPhotoAltText } from '../public-photo-alt';
import type { PublicPlaceHistory as PublicPlaceHistoryData } from '../types';
import { ProtectedPublicImage } from './protected-public-image';

interface PublicPlaceHistoryProps {
  anchorPostId: string;
  history: PublicPlaceHistoryData;
}

export function PublicPlaceHistory({ anchorPostId, history }: PublicPlaceHistoryProps) {
  return (
    <article className="public-place-history">
      <Link className="public-detail-back" href={`/posts/${anchorPostId}`}>
        ← 원래 기록으로 돌아가기
      </Link>

      <header className="public-place-header">
        <div>
          <p className="overline">PLACE HISTORY</p>
          <h1>{history.place.name}</h1>
          <p>
            한 장소에 쌓인 기억을 방문한 순서대로 모았습니다.
            각 기록에서 그날의 사진과 이야기를 다시 볼 수 있습니다.
          </p>
        </div>
        <div className="public-place-count" aria-label={`공개 방문 기록 ${history.visitCount}개`}>
          <strong>{history.visitCount}</strong>
          <span>VISITS</span>
          <small>공개 방문 기록</small>
        </div>
      </header>

      {history.place.googleMapsUrl && (
        <a
          className="public-place-map-link"
          href={history.place.googleMapsUrl}
          target="_blank"
          rel="noreferrer"
        >
          Google 지도에서 장소 보기 <span aria-hidden="true">↗</span>
        </a>
      )}

      <ol className="public-place-timeline">
        {history.visits.map((visit, index) => {
          const visitDate = `${visit.publicVisitYear}-${String(visit.publicVisitMonth).padStart(2, '0')}`;
          return (
            <li key={visit.id} className={visit.id === anchorPostId ? 'is-current' : undefined}>
              <span className="public-place-sequence" aria-hidden="true">
                {String(index + 1).padStart(2, '0')}
              </span>
              <Link href={`/posts/${visit.id}`}>
                <div className="public-place-cover">
                  {visit.cover ? (
                    <ProtectedPublicImage
                      src={getPublicCoverUrl(visit.cover.path)}
                      alt={resolvePublicPhotoAltText(visit.cover.altText, `${visit.title} 대표 사진`)}
                      sizes="(max-width: 640px) 100vw, 300px"
                      shieldClassName="public-place-cover-shield"
                    />
                  ) : (
                    <b>{visit.category === 'RESTAURANT' ? 'PLATE' : 'PLACE'}</b>
                  )}
                </div>
                <div className="public-place-visit-copy">
                  <p>{visit.category === 'RESTAURANT' ? '맛집' : '여행지'}</p>
                  <h2>{visit.title}</h2>
                  <span>{visit.summary ?? '이 방문의 한줄평은 아직 준비 중입니다.'}</span>
                  <time dateTime={visitDate}>
                    {visit.publicVisitYear}년 {visit.publicVisitMonth}월
                  </time>
                  <strong>{visit.id === anchorPostId ? '현재 보고 있던 기록' : '기록 읽기'} →</strong>
                </div>
              </Link>
            </li>
          );
        })}
      </ol>

      {history.visitCount === 1 && (
        <p className="public-place-first-visit">
          아직 공개된 방문은 한 번입니다. 다시 찾은 날의 기록이 생기면 이곳에 이어집니다.
        </p>
      )}
    </article>
  );
}
