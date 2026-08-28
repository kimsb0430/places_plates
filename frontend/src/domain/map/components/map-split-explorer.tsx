'use client';

import Link from 'next/link';
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
import type { MapPostMarker, MapViewState } from '../types';
import { GoogleMapExplorer } from './google-map-explorer';

interface MapSplitExplorerProps {
  apiKey: string;
  mapId?: string;
  posts: MapPostMarker[];
  initialQuery: string;
  initialSelectedPostId?: string;
  initialView?: MapViewState;
  initiallyLoaded: boolean;
}

export function MapSplitExplorer({
  apiKey,
  mapId,
  posts,
  initialQuery,
  initialSelectedPostId,
  initialView,
  initiallyLoaded,
}: MapSplitExplorerProps) {
  const [query, setQuery] = useState(initialQuery);
  const [requestedSelectedPostId, setRequestedSelectedPostId] = useState<string | null>(initialSelectedPostId ?? null);
  const [highlightedPostId, setHighlightedPostId] = useState<string | null>(null);
  const [viewportPostIds, setViewportPostIds] = useState<string[] | null>(null);
  const [shouldLimitToViewport, setShouldLimitToViewport] = useState(!initialQuery.trim());
  const deferredQuery = useDeferredValue(query.trim().toLocaleLowerCase('ko-KR'));
  const filteredPosts = useMemo(() => posts.filter((post) => matchesQuery(post, deferredQuery)), [deferredQuery, posts]);
  const selectedPostId = filteredPosts.some((post) => post.id === requestedSelectedPostId)
    ? requestedSelectedPostId
    : null;
  const visiblePostIds = useMemo(() => filteredPosts.map((post) => post.id), [filteredPosts]);
  const viewportPostIdSet = useMemo(
    () => viewportPostIds ? new Set(viewportPostIds) : null,
    [viewportPostIds],
  );
  const listedPosts = useMemo(() => {
    if (!shouldLimitToViewport || !viewportPostIdSet) return filteredPosts;
    return filteredPosts.filter((post) => viewportPostIdSet.has(post.id));
  }, [filteredPosts, shouldLimitToViewport, viewportPostIdSet]);

  useEffect(() => {
    replaceMapUrlParameters({ q: query.trim() || null });
  }, [query]);

  useEffect(() => {
    replaceMapUrlParameters({ post: selectedPostId });
    if (selectedPostId) {
      const shouldReduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      document.getElementById(`map-post-${selectedPostId}`)?.scrollIntoView({
        behavior: shouldReduceMotion ? 'auto' : 'smooth',
        block: 'nearest',
        inline: 'nearest',
      });
    }
  }, [selectedPostId]);

  const handleViewChange = useCallback((view: MapViewState) => {
    replaceMapUrlParameters({
      lat: view.latitude.toFixed(5),
      lng: view.longitude.toFixed(5),
      zoom: view.zoom.toFixed(2),
    });
  }, []);

  const handleMapLoadRequest = useCallback(() => {
    replaceMapUrlParameters({ map: '1' });
  }, []);

  return (
    <section className="map-split-explorer" aria-label="지도와 축소 게시물 목록">
      <GoogleMapExplorer
        apiKey={apiKey}
        mapId={mapId}
        posts={posts}
        visiblePostIds={visiblePostIds}
        selectedPostId={selectedPostId}
        highlightedPostId={highlightedPostId ?? selectedPostId}
        initialView={initialView}
        initiallyLoaded={initiallyLoaded}
        onLoadRequest={handleMapLoadRequest}
        onSelectPost={setRequestedSelectedPostId}
        onViewChange={handleViewChange}
        onViewportPostIdsChange={setViewportPostIds}
      />

      <aside className="map-compact-panel">
        <div className="map-compact-panel-heading">
          <div>
            <p className="overline">POSTS ON MAP</p>
            <h2>지도 기록</h2>
          </div>
          <output aria-live="polite">{listedPosts.length}개</output>
        </div>

        <div className="map-post-search" role="search">
          <label htmlFor="map-post-query">장소 또는 기록 검색</label>
          <div>
            <input
              id="map-post-query"
              type="search"
              value={query}
              maxLength={80}
              placeholder="제목이나 장소명"
              onChange={(event) => {
                setQuery(event.target.value);
                if (event.target.value.trim()) setShouldLimitToViewport(false);
              }}
            />
            {query && <button type="button" onClick={() => setQuery('')}>지우기</button>}
          </div>
        </div>

        <label className="map-viewport-toggle">
          <input
            type="checkbox"
            checked={shouldLimitToViewport}
            onChange={(event) => setShouldLimitToViewport(event.target.checked)}
          />
          현재 지도 영역만 보기
        </label>

        {listedPosts.length > 0 ? (
          <div className="map-compact-list">
            {listedPosts.map((post) => {
              const isSelected = selectedPostId === post.id;
              return (
                <article
                  id={`map-post-${post.id}`}
                  key={post.id}
                  className={isSelected ? 'map-compact-card is-selected' : 'map-compact-card'}
                  onMouseEnter={() => setHighlightedPostId(post.id)}
                  onMouseLeave={() => setHighlightedPostId(null)}
                >
                  <button
                    type="button"
                    className="map-compact-card-select"
                    aria-pressed={isSelected}
                    onClick={() => setRequestedSelectedPostId(post.id)}
                    onFocus={() => setHighlightedPostId(post.id)}
                    onBlur={() => setHighlightedPostId(null)}
                  >
                    <i className={post.category === 'RESTAURANT' ? 'is-restaurant' : 'is-destination'} aria-hidden="true">
                      {post.category === 'RESTAURANT' ? '맛' : '여'}
                    </i>
                    <span>
                      <small>{categoryLabel(post)} · {formatVisitMonth(post)}</small>
                      <strong>{post.title}</strong>
                      <em>{post.placeName}</em>
                    </span>
                  </button>
                  <Link href={`/posts/${post.id}`}>기록 읽기 <span aria-hidden="true">→</span></Link>
                </article>
              );
            })}
          </div>
        ) : (
          <div className="map-compact-empty" role="status">
            <strong>조건에 맞는 지도 기록이 없습니다.</strong>
            <p>검색어를 지우거나 현재 지도 영역 제한을 해제해보세요.</p>
          </div>
        )}
      </aside>
    </section>
  );
}

function matchesQuery(post: MapPostMarker, query: string): boolean {
  if (!query) return true;
  return `${post.title} ${post.placeName}`.toLocaleLowerCase('ko-KR').includes(query);
}

function replaceMapUrlParameters(values: Record<string, string | null>): void {
  const url = new URL(window.location.href);
  for (const [key, value] of Object.entries(values)) {
    if (value) url.searchParams.set(key, value);
    else url.searchParams.delete(key);
  }
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}`);
}

function categoryLabel(post: MapPostMarker): string {
  return post.category === 'RESTAURANT' ? '맛집' : '여행지';
}

function formatVisitMonth(post: MapPostMarker): string {
  return `${post.publicVisitYear}.${String(post.publicVisitMonth).padStart(2, '0')}`;
}
