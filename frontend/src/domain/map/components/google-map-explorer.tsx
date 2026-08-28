'use client';

import { importLibrary, setOptions } from '@googlemaps/js-api-loader';
import {
  MarkerClusterer,
  SuperClusterAlgorithm,
  type Marker as ClusterMarker,
  type Renderer,
} from '@googlemaps/markerclusterer';
import { useEffect, useRef, useState } from 'react';
import type { MapPostMarker, MapViewState } from '../types';
import {
  countPostsWithinMapBounds,
  getPostsWithinMapBounds,
  type MapViewportPostCounts,
} from '../map-viewport-count';

interface GoogleMapExplorerProps {
  apiKey: string;
  mapId?: string;
  posts: MapPostMarker[];
  visiblePostIds: string[];
  selectedPostId: string | null;
  highlightedPostId: string | null;
  initialView?: MapViewState;
  initiallyLoaded: boolean;
  onLoadRequest: () => void;
  onSelectPost: (postId: string) => void;
  onViewChange: (view: MapViewState) => void;
  onViewportPostIdsChange: (postIds: string[]) => void;
}

interface MapMarkerRecord {
  marker: ClusterMarker;
  post: MapPostMarker;
}

let configuredApiKey: string | null = null;

export function GoogleMapExplorer({
  apiKey,
  mapId,
  posts,
  visiblePostIds,
  selectedPostId,
  highlightedPostId,
  initialView,
  initiallyLoaded,
  onLoadRequest,
  onSelectPost,
  onViewChange,
  onViewportPostIdsChange,
}: GoogleMapExplorerProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<google.maps.Map | null>(null);
  const clustererRef = useRef<MarkerClusterer | null>(null);
  const infoWindowRef = useRef<google.maps.InfoWindow | null>(null);
  const markerRecordsRef = useRef<Map<string, MapMarkerRecord>>(new Map());
  const refreshViewportRef = useRef<() => void>(() => undefined);
  const visiblePostIdsRef = useRef<Set<string>>(new Set(visiblePostIds));
  const selectedPostIdRef = useRef<string | null>(selectedPostId);
  const onSelectPostRef = useRef(onSelectPost);
  const onViewChangeRef = useRef(onViewChange);
  const onViewportPostIdsChangeRef = useRef(onViewportPostIdsChange);
  const [shouldLoadMap, setShouldLoadMap] = useState(initiallyLoaded);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [viewportCounts, setViewportCounts] = useState<MapViewportPostCounts | null>(null);

  useEffect(() => {
    visiblePostIdsRef.current = new Set(visiblePostIds);
    selectedPostIdRef.current = selectedPostId;
    onSelectPostRef.current = onSelectPost;
    onViewChangeRef.current = onViewChange;
    onViewportPostIdsChangeRef.current = onViewportPostIdsChange;
  }, [onSelectPost, onViewChange, onViewportPostIdsChange, selectedPostId, visiblePostIds]);

  useEffect(() => {
    if (!shouldLoadMap || !mapContainerRef.current) return;

    let isCancelled = false;
    const activeMarkers: ClusterMarker[] = [];
    const activeMarkerCleanups: Array<() => void> = [];
    const markerCategories = new Map<ClusterMarker, MapPostMarker['category']>();
    let activeClusterer: MarkerClusterer | null = null;
    let activeInfoWindow: google.maps.InfoWindow | null = null;
    let activeIdleListener: google.maps.MapsEventListener | null = null;

    async function initializeMap() {
      setIsLoading(true);
      setErrorMessage(null);
      setViewportCounts(null);
      try {
        if (configuredApiKey && configuredApiKey !== apiKey) {
          throw new Error('Google Maps API key changed after initialization.');
        }
        if (!configuredApiKey) {
          setOptions({
            key: apiKey,
            v: 'weekly',
            language: 'ko',
            region: 'KR',
            authReferrerPolicy: 'origin',
          });
          configuredApiKey = apiKey;
        }
        const { Map: GoogleMap, InfoWindow } = await importLibrary('maps') as google.maps.MapsLibrary;
        const markerLibrary = mapId
          ? await importLibrary('marker') as google.maps.MarkerLibrary
          : null;
        if (isCancelled || !mapContainerRef.current) return;

        const map = new GoogleMap(mapContainerRef.current, {
          center: initialView
            ? { lat: initialView.latitude, lng: initialView.longitude }
            : { lat: posts[0].latitude, lng: posts[0].longitude },
          zoom: initialView?.zoom ?? 12,
          mapId: mapId || undefined,
          clickableIcons: false,
          fullscreenControl: true,
          mapTypeControl: false,
          streetViewControl: false,
        });
        activeInfoWindow = new InfoWindow();
        const markerRecords = new Map<string, MapMarkerRecord>();

        for (const post of posts) {
          const position = { lat: post.latitude, lng: post.longitude };
          const title = `${categoryLabel(post.category)}: ${post.title}`;
          const marker = markerLibrary
            ? createAdvancedMarker(markerLibrary, position, title, post)
            : createClassicMarker(position, title, post);
          const openPostInfo = () => {
            if (!activeInfoWindow) return;
            onSelectPostRef.current(post.id);
            openMapPostInfo(map, activeInfoWindow, marker, post);
          };
          if (markerLibrary && 'addEventListener' in marker) {
            marker.addEventListener('gmp-click', openPostInfo);
            activeMarkerCleanups.push(() => marker.removeEventListener('gmp-click', openPostInfo));
          } else {
            const listener = marker.addListener('click', openPostInfo);
            activeMarkerCleanups.push(() => listener.remove());
          }
          activeMarkers.push(marker);
          markerRecords.set(post.id, { marker, post });
          markerCategories.set(marker, post.category);
        }

        const visibleMarkers = [...markerRecords.values()]
          .filter((record) => visiblePostIdsRef.current.has(record.post.id))
          .map((record) => record.marker);
        activeClusterer = new MarkerClusterer({
          map,
          markers: visibleMarkers,
          algorithm: new SuperClusterAlgorithm({ radius: 72, maxZoom: 17 }),
          renderer: createClusterRenderer(markerLibrary, markerCategories),
        });
        mapRef.current = map;
        clustererRef.current = activeClusterer;
        infoWindowRef.current = activeInfoWindow;
        markerRecordsRef.current = markerRecords;

        const refreshViewport = () => {
          const currentBounds = map.getBounds();
          if (isCancelled || !currentBounds) return;
          const visiblePosts = posts.filter((post) => visiblePostIdsRef.current.has(post.id));
          const boundsLiteral = currentBounds.toJSON();
          const viewportPosts = getPostsWithinMapBounds(visiblePosts, boundsLiteral);
          setViewportCounts(countPostsWithinMapBounds(visiblePosts, boundsLiteral));
          onViewportPostIdsChangeRef.current(viewportPosts.map((post) => post.id));
          const center = map.getCenter();
          const zoom = map.getZoom();
          if (center && zoom !== undefined) {
            onViewChangeRef.current({
              latitude: center.lat(),
              longitude: center.lng(),
              zoom,
            });
          }
        };
        refreshViewportRef.current = refreshViewport;
        activeIdleListener = map.addListener('idle', refreshViewport);

        if (!initialView) {
          const initialVisiblePosts = posts.filter((post) => visiblePostIdsRef.current.has(post.id));
          const focusPosts = initialVisiblePosts.length > 0 ? initialVisiblePosts : posts;
          const focusBounds = new google.maps.LatLngBounds();
          focusPosts.forEach((post) => focusBounds.extend({ lat: post.latitude, lng: post.longitude }));
          if (focusPosts.length === 1) {
            map.setCenter(focusBounds.getCenter());
            map.setZoom(14);
          } else {
            map.fitBounds(focusBounds, 64);
          }
        }

        const initialSelectedPostId = selectedPostIdRef.current;
        const selectedRecord = initialSelectedPostId ? markerRecords.get(initialSelectedPostId) : undefined;
        if (selectedRecord && visiblePostIdsRef.current.has(selectedRecord.post.id)) {
          focusMapPost(map, activeInfoWindow, selectedRecord);
        }
      } catch {
        if (!isCancelled) {
          setErrorMessage('Google 지도를 불러오지 못했습니다. API 키 제한과 Maps JavaScript API 활성화를 확인해주세요.');
        }
      } finally {
        if (!isCancelled) setIsLoading(false);
      }
    }

    void initializeMap();
    return () => {
      isCancelled = true;
      activeIdleListener?.remove();
      activeMarkerCleanups.forEach((cleanup) => cleanup());
      activeInfoWindow?.close();
      activeClusterer?.clearMarkers();
      activeClusterer?.setMap(null);
      activeMarkers.forEach((marker) => {
        if ('setMap' in marker) marker.setMap(null);
        else marker.map = null;
      });
      if (mapRef.current) mapRef.current = null;
      if (clustererRef.current === activeClusterer) clustererRef.current = null;
      if (infoWindowRef.current === activeInfoWindow) infoWindowRef.current = null;
      markerRecordsRef.current = new Map();
      refreshViewportRef.current = () => undefined;
    };
  }, [apiKey, initialView, mapId, posts, shouldLoadMap]);

  useEffect(() => {
    const clusterer = clustererRef.current;
    if (!clusterer) return;
    clusterer.clearMarkers(true);
    const visibleMarkers = visiblePostIds
      .map((postId) => markerRecordsRef.current.get(postId)?.marker)
      .filter((marker): marker is ClusterMarker => Boolean(marker));
    clusterer.addMarkers(visibleMarkers);
    const currentSelectedPostId = selectedPostIdRef.current;
    if (currentSelectedPostId && !visiblePostIdsRef.current.has(currentSelectedPostId)) {
      infoWindowRef.current?.close();
    }
    refreshViewportRef.current();
  }, [visiblePostIds]);

  useEffect(() => {
    markerRecordsRef.current.forEach((record) => {
      setMapMarkerHighlighted(record, record.post.id === highlightedPostId);
    });
  }, [highlightedPostId]);

  useEffect(() => {
    const map = mapRef.current;
    const infoWindow = infoWindowRef.current;
    if (!map || !infoWindow) return;
    if (!selectedPostId) {
      infoWindow.close();
      return;
    }
    const record = markerRecordsRef.current.get(selectedPostId);
    if (record && visiblePostIdsRef.current.has(selectedPostId)) {
      focusMapPost(map, infoWindow, record);
    }
  }, [selectedPostId]);

  if (!apiKey) {
    return (
      <div className="google-map-gate" role="status">
        <p className="overline">MAP CONFIGURATION REQUIRED</p>
        <h2>지도 브라우저 키 연결이 필요합니다.</h2>
        <p>Vercel에 Maps JavaScript API 전용 공개 키를 설정하면 카테고리 마커가 표시됩니다.</p>
      </div>
    );
  }

  return (
    <div className="google-map-stage" aria-busy={isLoading}>
      {!shouldLoadMap && (
        <div className="google-map-gate">
          <p className="overline">ON-DEMAND GOOGLE MAP</p>
          <h2>{visiblePostIds.length}개의 공개 기록을 지도에서 봅니다.</h2>
          <p>지도는 버튼을 누를 때만 불러와 무료 사용량을 아낍니다.</p>
          <button type="button" onClick={() => { setShouldLoadMap(true); onLoadRequest(); }}>
            Google 지도 불러오기 <span aria-hidden="true">→</span>
          </button>
        </div>
      )}
      <div
        ref={mapContainerRef}
        className={shouldLoadMap ? 'google-map-canvas is-visible' : 'google-map-canvas'}
        aria-label="공개 맛집과 여행지 기록 지도"
      />
      {isLoading && <p className="google-map-status">지도를 불러오는 중입니다…</p>}
      {shouldLoadMap && !isLoading && !errorMessage && viewportCounts && (
        <output className="google-map-viewport-count" aria-live="polite" aria-atomic="true">
          <strong>현재 지도 영역 {viewportCounts.total}개</strong>
          <span>맛집 {viewportCounts.restaurant} · 여행지 {viewportCounts.destination}</span>
        </output>
      )}
      {shouldLoadMap && !isLoading && !errorMessage && visiblePostIds.length > 1 && (
        <p className="google-map-cluster-guide">숫자 마커를 누르면 포함된 기록이 보이도록 확대됩니다.</p>
      )}
      {errorMessage && (
        <div className="google-map-error" role="alert">
          <p>{errorMessage}</p>
          <button type="button" onClick={() => setShouldLoadMap(false)}>닫기</button>
        </div>
      )}
    </div>
  );
}

function createAdvancedMarker(
  markerLibrary: google.maps.MarkerLibrary,
  position: google.maps.LatLngLiteral,
  title: string,
  post: MapPostMarker,
): google.maps.marker.AdvancedMarkerElement {
  const content = document.createElement('div');
  content.className = `category-map-marker ${post.category === 'RESTAURANT' ? 'is-restaurant' : 'is-destination'}`;
  content.textContent = post.category === 'RESTAURANT' ? '맛' : '여';
  content.setAttribute('aria-hidden', 'true');
  return new markerLibrary.AdvancedMarkerElement({ position, title, content, gmpClickable: true });
}

function createClassicMarker(
  position: google.maps.LatLngLiteral,
  title: string,
  post: MapPostMarker,
): google.maps.Marker {
  return new google.maps.Marker({
    position,
    title,
    label: {
      text: post.category === 'RESTAURANT' ? '맛' : '여',
      color: '#ffffff',
      fontSize: '11px',
      fontWeight: '800',
    },
    icon: createClassicMarkerIcon(post, false),
  });
}

function focusMapPost(
  map: google.maps.Map,
  infoWindow: google.maps.InfoWindow,
  record: MapMarkerRecord,
): void {
  map.panTo({ lat: record.post.latitude, lng: record.post.longitude });
  if ((map.getZoom() ?? 0) < 18) map.setZoom(18);
  setMapMarkerHighlighted(record, true);
  openMapPostInfo(map, infoWindow, record.marker, record.post);
}

function openMapPostInfo(
  map: google.maps.Map,
  infoWindow: google.maps.InfoWindow,
  marker: ClusterMarker,
  post: MapPostMarker,
): void {
  infoWindow.setContent(createInfoWindowContent(post));
  infoWindow.open({ map, anchor: marker });
}

function setMapMarkerHighlighted(record: MapMarkerRecord, isHighlighted: boolean): void {
  if ('setIcon' in record.marker) {
    record.marker.setIcon(createClassicMarkerIcon(record.post, isHighlighted));
    record.marker.setZIndex(isHighlighted ? 900 : undefined);
    return;
  }
  if (record.marker.content instanceof HTMLElement) {
    record.marker.content.classList.toggle('is-highlighted', isHighlighted);
  }
  record.marker.zIndex = isHighlighted ? 900 : null;
}

function createClassicMarkerIcon(
  post: MapPostMarker,
  isHighlighted: boolean,
): google.maps.Symbol {
  return {
    path: google.maps.SymbolPath.CIRCLE,
    fillColor: post.category === 'RESTAURANT' ? '#c24c20' : '#477f6c',
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeOpacity: 1,
    strokeWeight: isHighlighted ? 5 : 3,
    scale: isHighlighted ? 20 : 16,
  };
}

function createClusterRenderer(
  markerLibrary: google.maps.MarkerLibrary | null,
  markerCategories: Map<ClusterMarker, MapPostMarker['category']>,
): Renderer {
  return {
    render({ count, position, markers }) {
      const category = resolveClusterCategory(markers, markerCategories);
      const title = `${count}개의 공개 기록. 누르면 확대됩니다.`;
      if (markerLibrary) {
        const content = document.createElement('div');
        content.className = `map-cluster-marker is-${category.toLowerCase()}`;
        content.textContent = String(count);
        content.setAttribute('aria-label', title);
        return new markerLibrary.AdvancedMarkerElement({
          position,
          title,
          content,
          gmpClickable: true,
          zIndex: 1000 + count,
        });
      }
      return new google.maps.Marker({
        position,
        title,
        zIndex: 1000 + count,
        label: {
          text: String(count),
          color: '#ffffff',
          fontSize: '12px',
          fontWeight: '900',
        },
        icon: {
          path: google.maps.SymbolPath.CIRCLE,
          fillColor: clusterColor(category),
          fillOpacity: 1,
          strokeColor: '#ffffff',
          strokeOpacity: 1,
          strokeWeight: 4,
          scale: 22,
        },
      });
    },
  };
}

function resolveClusterCategory(
  markers: ClusterMarker[],
  markerCategories: Map<ClusterMarker, MapPostMarker['category']>,
): MapPostMarker['category'] | 'MIXED' {
  const categories = new Set(markers.map((marker) => markerCategories.get(marker)).filter(Boolean));
  if (categories.size !== 1) return 'MIXED';
  return categories.values().next().value ?? 'MIXED';
}

function clusterColor(category: MapPostMarker['category'] | 'MIXED'): string {
  if (category === 'RESTAURANT') return '#c24c20';
  if (category === 'DESTINATION') return '#477f6c';
  return '#17342d';
}

function createInfoWindowContent(post: MapPostMarker): HTMLElement {
  const content = document.createElement('article');
  content.className = 'map-info-card';
  const category = document.createElement('p');
  category.textContent = `${categoryLabel(post.category)} · ${formatVisitMonth(post)}`;
  const title = document.createElement('strong');
  title.textContent = post.title;
  const place = document.createElement('span');
  place.textContent = post.placeName;
  const link = document.createElement('a');
  link.href = `/posts/${post.id}`;
  link.textContent = '기록 읽기 →';
  content.appendChild(category);
  content.appendChild(title);
  content.appendChild(place);
  content.appendChild(link);
  return content;
}

function categoryLabel(category: MapPostMarker['category']): string {
  return category === 'RESTAURANT' ? '맛집' : '여행지';
}

function formatVisitMonth(post: MapPostMarker): string {
  return `${post.publicVisitYear}.${String(post.publicVisitMonth).padStart(2, '0')}`;
}
