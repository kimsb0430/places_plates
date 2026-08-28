'use client';

import { importLibrary, setOptions } from '@googlemaps/js-api-loader';
import {
  MarkerClusterer,
  SuperClusterAlgorithm,
  type Marker as ClusterMarker,
  type Renderer,
} from '@googlemaps/markerclusterer';
import { useEffect, useRef, useState } from 'react';
import type { MapPostMarker } from '../types';

interface GoogleMapExplorerProps {
  apiKey: string;
  mapId?: string;
  posts: MapPostMarker[];
}

let configuredApiKey: string | null = null;

export function GoogleMapExplorer({ apiKey, mapId, posts }: GoogleMapExplorerProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const [shouldLoadMap, setShouldLoadMap] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!shouldLoadMap || !mapContainerRef.current) return;

    let isCancelled = false;
    const activeMarkers: ClusterMarker[] = [];
    const markerCategories = new Map<ClusterMarker, MapPostMarker['category']>();
    let activeClusterer: MarkerClusterer | null = null;
    let activeInfoWindow: google.maps.InfoWindow | null = null;

    async function initializeMap() {
      setIsLoading(true);
      setErrorMessage(null);
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
        const { Map, InfoWindow } = await importLibrary('maps') as google.maps.MapsLibrary;
        const markerLibrary = mapId
          ? await importLibrary('marker') as google.maps.MarkerLibrary
          : null;
        if (isCancelled || !mapContainerRef.current) return;

        const bounds = new google.maps.LatLngBounds();
        const map = new Map(mapContainerRef.current, {
          center: { lat: posts[0].latitude, lng: posts[0].longitude },
          zoom: 12,
          mapId: mapId || undefined,
          clickableIcons: false,
          fullscreenControl: true,
          mapTypeControl: false,
          streetViewControl: false,
        });
        activeInfoWindow = new InfoWindow();

        for (const post of posts) {
          const position = { lat: post.latitude, lng: post.longitude };
          const title = `${categoryLabel(post.category)}: ${post.title}`;
          const marker = markerLibrary
            ? createAdvancedMarker(markerLibrary, position, title, post)
            : createClassicMarker(position, title, post);
          const openPostInfo = () => {
            if (!activeInfoWindow) return;
            activeInfoWindow.setContent(createInfoWindowContent(post));
            activeInfoWindow.open({ map, anchor: marker });
          };
          if (markerLibrary && 'addEventListener' in marker) {
            marker.addEventListener('gmp-click', openPostInfo);
          } else {
            marker.addListener('click', openPostInfo);
          }
          activeMarkers.push(marker);
          markerCategories.set(marker, post.category);
          bounds.extend(position);
        }

        activeClusterer = new MarkerClusterer({
          map,
          markers: activeMarkers,
          algorithm: new SuperClusterAlgorithm({ radius: 72, maxZoom: 17 }),
          renderer: createClusterRenderer(markerLibrary, markerCategories),
        });

        if (posts.length === 1) {
          map.setCenter(bounds.getCenter());
          map.setZoom(14);
        } else {
          map.fitBounds(bounds, 64);
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
      activeInfoWindow?.close();
      activeClusterer?.clearMarkers();
      activeClusterer?.setMap(null);
      activeMarkers.forEach((marker) => {
        if ('setMap' in marker) marker.setMap(null);
        else marker.map = null;
      });
    };
  }, [apiKey, mapId, posts, shouldLoadMap]);

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
          <h2>{posts.length}개의 공개 기록을 지도에서 봅니다.</h2>
          <p>지도는 버튼을 누를 때만 불러와 무료 사용량을 아낍니다.</p>
          <button type="button" onClick={() => setShouldLoadMap(true)}>
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
      {shouldLoadMap && !isLoading && !errorMessage && posts.length > 1 && (
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
    icon: {
      path: google.maps.SymbolPath.CIRCLE,
      fillColor: post.category === 'RESTAURANT' ? '#c24c20' : '#477f6c',
      fillOpacity: 1,
      strokeColor: '#ffffff',
      strokeOpacity: 1,
      strokeWeight: 3,
      scale: 16,
    },
  });
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
