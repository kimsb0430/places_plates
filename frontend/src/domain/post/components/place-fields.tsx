'use client';

import { FormEvent, useRef, useState } from 'react';
import {
  connectDraftPlace,
  disconnectDraftPlace,
  DraftPostApiError,
  searchPlaces,
} from '../api/draft-post-api';
import type { DraftPost, Place, PlaceConnectionInput, PlaceSearchResult } from '../types';

interface PlaceFieldsProps {
  draftPostId: string;
  value: Place | null;
  onSaved: (draft: DraftPost) => void;
  onUnauthorized: () => void;
}

type RequestState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string };

export function PlaceFields({
  draftPostId,
  value,
  onSaved,
  onUnauthorized,
}: PlaceFieldsProps) {
  const [mode, setMode] = useState<'GOOGLE' | 'MANUAL'>('GOOGLE');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PlaceSearchResult[]>([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [searchState, setSearchState] = useState<RequestState>({ status: 'idle' });
  const [saveState, setSaveState] = useState<RequestState>({ status: 'idle' });
  const searchAbort = useRef<AbortController | null>(null);

  const handleSearch = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (query.trim().length < 2) {
      setSearchState({ status: 'error', message: '검색어를 2자 이상 입력해주세요.' });
      return;
    }
    searchAbort.current?.abort();
    searchAbort.current = new AbortController();
    setSearchState({ status: 'loading' });
    try {
      const places = await searchPlaces(query.trim(), searchAbort.current.signal);
      setResults(places);
      setHasSearched(true);
      setSearchState({ status: 'idle' });
    } catch (error) {
      if (searchAbort.current.signal.aborted) return;
      handleError(error, setSearchState, onUnauthorized);
    }
  };

  const savePlace = async (input: PlaceConnectionInput) => {
    setSaveState({ status: 'loading' });
    try {
      onSaved(await connectDraftPlace(draftPostId, input));
      setSaveState({ status: 'idle' });
    } catch (error) {
      handleError(error, setSaveState, onUnauthorized);
    }
  };

  const removePlace = async () => {
    setSaveState({ status: 'loading' });
    try {
      onSaved(await disconnectDraftPlace(draftPostId));
      setSaveState({ status: 'idle' });
    } catch (error) {
      handleError(error, setSaveState, onUnauthorized);
    }
  };

  return (
    <section className="place-section" aria-labelledby="place-heading">
      <div className="place-heading">
        <div>
          <p className="overline">PLACE CONNECTION</p>
          <h2 id="place-heading">장소 연결</h2>
        </div>
        <span>게시 전 필수</span>
      </div>

      {value && (
        <div className="connected-place">
          <div>
            <small translate={value.source === 'GOOGLE' ? 'no' : undefined}>
              {value.source === 'GOOGLE' ? 'Google Maps' : '직접 입력'}
            </small>
            <strong>{value.name}</strong>
            {value.formattedAddress && <p>{value.formattedAddress}</p>}
            {value.latitude !== null && value.longitude !== null && (
              <code>{value.latitude.toFixed(6)}, {value.longitude.toFixed(6)}</code>
            )}
          </div>
          <div className="connected-place-actions">
            {value.googleMapsUrl && (
              <a href={value.googleMapsUrl} target="_blank" rel="noreferrer">Google Maps에서 보기</a>
            )}
            <button type="button" disabled={saveState.status === 'loading'} onClick={removePlace}>
              연결 해제
            </button>
          </div>
        </div>
      )}

      <div className="place-mode-tabs" aria-label="장소 입력 방식">
        <button
          type="button"
          className={mode === 'GOOGLE' ? 'is-active' : ''}
          aria-pressed={mode === 'GOOGLE'}
          onClick={() => setMode('GOOGLE')}
        >
          Google에서 검색
        </button>
        <button
          type="button"
          className={mode === 'MANUAL' ? 'is-active' : ''}
          aria-pressed={mode === 'MANUAL'}
          onClick={() => setMode('MANUAL')}
        >
          직접 입력
        </button>
      </div>

      {mode === 'GOOGLE' ? (
        <GooglePlaceSearch
          query={query}
          results={results}
          hasSearched={hasSearched}
          searchState={searchState}
          saveState={saveState}
          onQueryChange={(value) => {
            setQuery(value);
            setHasSearched(false);
          }}
          onSearch={handleSearch}
          onSelect={(place) => savePlace({ ...place, source: 'GOOGLE' })}
        />
      ) : (
        <ManualPlaceForm
          isSaving={saveState.status === 'loading'}
          onSave={savePlace}
        />
      )}

      {saveState.status === 'error' && <p className="place-error" role="alert">{saveState.message}</p>}
    </section>
  );
}

interface GooglePlaceSearchProps {
  query: string;
  results: PlaceSearchResult[];
  hasSearched: boolean;
  searchState: RequestState;
  saveState: RequestState;
  onQueryChange: (value: string) => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
  onSelect: (place: PlaceSearchResult) => void;
}

function GooglePlaceSearch({
  query,
  results,
  hasSearched,
  searchState,
  saveState,
  onQueryChange,
  onSearch,
  onSelect,
}: GooglePlaceSearchProps) {
  return (
    <div className="google-place-search">
      <form onSubmit={onSearch}>
        <label htmlFor="place-query">가게·명소 이름</label>
        <div>
          <input
            id="place-query"
            value={query}
            maxLength={100}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="예: 교토 니시키 시장"
          />
          <button type="submit" disabled={searchState.status === 'loading'}>
            {searchState.status === 'loading' ? '검색 중…' : '검색'}
          </button>
        </div>
      </form>
      <p className="google-maps-attribution" translate="no">Google Maps</p>
      {searchState.status === 'error' && (
        <p className="place-error" role="alert">{searchState.message}</p>
      )}
      {searchState.status === 'idle' && hasSearched && results.length === 0 && (
        <p className="place-empty">검색 결과가 없으면 직접 입력으로 저장할 수 있습니다.</p>
      )}
      {results.length > 0 && (
        <ul className="place-results">
          {results.map((place) => (
            <li key={place.googlePlaceId}>
              <button
                type="button"
                disabled={saveState.status === 'loading'}
                onClick={() => onSelect(place)}
              >
                <strong>{place.name}</strong>
                <span>{place.formattedAddress ?? '주소 정보 없음'}</span>
                <small>이 장소 연결하기 →</small>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ManualPlaceForm({
  isSaving,
  onSave,
}: {
  isSaving: boolean;
  onSave: (input: PlaceConnectionInput) => void;
}) {
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [message, setMessage] = useState('');

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) {
      setMessage('장소 이름을 입력해주세요.');
      return;
    }
    if (Boolean(latitude) !== Boolean(longitude)) {
      setMessage('위도와 경도를 함께 입력해주세요.');
      return;
    }
    setMessage('');
    onSave({
      source: 'MANUAL',
      googlePlaceId: null,
      name: name.trim(),
      placeType: null,
      formattedAddress: address.trim() || null,
      latitude: latitude ? Number(latitude) : null,
      longitude: longitude ? Number(longitude) : null,
    });
  };

  return (
    <form className="manual-place-form" onSubmit={handleSubmit}>
      <label>
        <span>장소 이름 <b>필수</b></span>
        <input value={name} maxLength={200} onChange={(event) => setName(event.target.value)} />
      </label>
      <label>
        <span>주소 <em>선택</em></span>
        <input value={address} maxLength={500} onChange={(event) => setAddress(event.target.value)} />
      </label>
      <label>
        <span>위도 <em>선택</em></span>
        <input
          type="number"
          min="-90"
          max="90"
          step="0.000001"
          value={latitude}
          onChange={(event) => setLatitude(event.target.value)}
          placeholder="35.011636"
        />
      </label>
      <label>
        <span>경도 <em>선택</em></span>
        <input
          type="number"
          min="-180"
          max="180"
          step="0.000001"
          value={longitude}
          onChange={(event) => setLongitude(event.target.value)}
          placeholder="135.768029"
        />
      </label>
      <small>좌표를 함께 입력한 기록은 전체 공개로 게시할 때 공개 지도에 정확한 위치로 표시됩니다.</small>
      {message && <p className="place-error" role="alert">{message}</p>}
      <button type="submit" disabled={isSaving}>{isSaving ? '저장 중…' : '직접 입력 장소 저장'}</button>
    </form>
  );
}

function handleError(
  error: unknown,
  setState: (state: RequestState) => void,
  onUnauthorized: () => void,
) {
  if (error instanceof DraftPostApiError && error.status === 401) {
    onUnauthorized();
    return;
  }
  setState({
    status: 'error',
    message: error instanceof DraftPostApiError ? error.message : '장소 요청을 처리하지 못했습니다.',
  });
}
