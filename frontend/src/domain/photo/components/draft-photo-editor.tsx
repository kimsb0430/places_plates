'use client';

import { useEffect, useRef, useState } from 'react';
import Image from 'next/image';
import {
  DraftPhotoApiError,
  getDraftPhotos,
  getDraftPhotoThumbnail,
  updateDraftPhotos,
} from '../api/draft-photo-api';
import type { DraftPhoto, DraftPhotoEditItem } from '../types';

interface DraftPhotoEditorProps {
  draftPostId: string;
  onUnauthorized: () => void;
  onSaved?: () => void;
}

type LoadState = 'loading' | 'ready' | 'error';
type SaveState = 'saved' | 'saving' | 'error';

const AUTOSAVE_DELAY_MS = 600;

export function DraftPhotoEditor({ draftPostId, onUnauthorized, onSaved }: DraftPhotoEditorProps) {
  const [photos, setPhotos] = useState<DraftPhoto[]>([]);
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [saveState, setSaveState] = useState<SaveState>('saved');
  const [message, setMessage] = useState('');
  const [retryVersion, setRetryVersion] = useState(0);
  const lastSavedSnapshot = useRef('');

  useEffect(() => {
    let active = true;
    getDraftPhotos(draftPostId)
      .then((loadedPhotos) => {
        if (!active) return;
        lastSavedSnapshot.current = snapshot(loadedPhotos);
        setPhotos(loadedPhotos);
        setLoadState('ready');
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof DraftPhotoApiError && error.status === 401) {
          onUnauthorized();
          return;
        }
        setMessage(error instanceof DraftPhotoApiError
          ? error.message
          : '사진 정보를 불러오지 못했습니다.');
        setLoadState('error');
      });
    return () => {
      active = false;
    };
  }, [draftPostId, onUnauthorized]);

  useEffect(() => {
    if (loadState !== 'ready') return;
    const currentSnapshot = snapshot(photos);
    if (currentSnapshot === lastSavedSnapshot.current) return;

    const abortController = new AbortController();
    const timer = window.setTimeout(() => {
      setSaveState('saving');
      setMessage('사진 변경 내용을 저장하고 있습니다.');
      updateDraftPhotos(draftPostId, toEditItems(photos), abortController.signal)
        .then((savedPhotos) => {
          lastSavedSnapshot.current = snapshot(savedPhotos);
          setPhotos(savedPhotos);
          setSaveState('saved');
          setMessage('사진 변경 내용이 저장되었습니다.');
          onSaved?.();
        })
        .catch((error: unknown) => {
          if (abortController.signal.aborted) return;
          if (error instanceof DraftPhotoApiError && error.status === 401) {
            onUnauthorized();
            return;
          }
          setSaveState('error');
          setMessage(error instanceof DraftPhotoApiError
            ? error.message
            : '사진 변경 내용을 저장하지 못했습니다.');
        });
    }, AUTOSAVE_DELAY_MS);

    return () => {
      window.clearTimeout(timer);
      abortController.abort();
    };
  }, [draftPostId, loadState, onSaved, onUnauthorized, photos, retryVersion]);

  const movePhoto = (index: number, direction: -1 | 1) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= photos.length) return;
    setPhotos((current) => {
      const reordered = [...current];
      [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
      return reordered.map((photo, displayOrder) => ({ ...photo, displayOrder }));
    });
    setMessage(`${index + 1}번째 사진을 ${targetIndex + 1}번째로 이동했습니다.`);
  };

  const selectCover = (photoId: string) => {
    setPhotos((current) => current.map((photo) => ({
      ...photo,
      cover: photo.id === photoId,
    })));
    setMessage('대표 사진을 변경했습니다.');
  };

  const updateAltText = (photoId: string, altText: string) => {
    setPhotos((current) => current.map((photo) => (
      photo.id === photoId ? { ...photo, altText } : photo
    )));
  };

  return (
    <section className="draft-photo-section" aria-labelledby="draft-photo-heading">
      <div className="draft-photo-heading">
        <div>
          <p className="login-status">PHOTO STORY</p>
          <h2 id="draft-photo-heading">사진 구성</h2>
          <p>순서, 대표 사진, 화면 읽기용 설명을 정리하세요.</p>
        </div>
        <PhotoSaveStatus loadState={loadState} saveState={saveState} />
      </div>

      <p className="sr-only" aria-live="polite">{message}</p>

      {loadState === 'loading' && <p className="draft-photo-empty">사진을 불러오고 있습니다.</p>}
      {loadState === 'error' && (
        <div className="draft-photo-empty" role="alert">
          <p>{message}</p>
          <button type="button" onClick={() => window.location.reload()}>다시 불러오기</button>
        </div>
      )}
      {loadState === 'ready' && photos.length === 0 && (
        <p className="draft-photo-empty">처리가 완료된 사진이 이곳에 표시됩니다.</p>
      )}
      {loadState === 'ready' && photos.length > 0 && (
        <ol className="draft-photo-list">
          {photos.map((photo, index) => (
            <li key={photo.id} className={photo.cover ? 'is-cover' : ''}>
              <DraftPhotoThumbnail photo={photo} position={index + 1} />
              <div className="draft-photo-fields">
                <div className="draft-photo-meta">
                  <strong>{index + 1}번째 사진</strong>
                  <span>{photo.processingStatus === 'READY' ? '처리 완료' : '처리 중'}</span>
                </div>
                <div className="draft-photo-controls" aria-label={`${index + 1}번째 사진 순서 변경`}>
                  <button
                    type="button"
                    disabled={index === 0}
                    onClick={() => movePhoto(index, -1)}
                    aria-label={`${index + 1}번째 사진을 앞으로 이동`}
                  >앞으로</button>
                  <button
                    type="button"
                    disabled={index === photos.length - 1}
                    onClick={() => movePhoto(index, 1)}
                    aria-label={`${index + 1}번째 사진을 뒤로 이동`}
                  >뒤로</button>
                  <button
                    type="button"
                    className="cover-button"
                    aria-pressed={photo.cover}
                    onClick={() => selectCover(photo.id)}
                  >{photo.cover ? '대표 사진' : '대표로 설정'}</button>
                </div>
                <label htmlFor={`photo-alt-${photo.id}`}>
                  <span>대체 텍스트 <em>선택</em></span>
                  <textarea
                    id={`photo-alt-${photo.id}`}
                    value={photo.altText ?? ''}
                    maxLength={500}
                    rows={2}
                    onChange={(event) => updateAltText(photo.id, event.target.value)}
                    placeholder="사진을 보지 못하는 사람도 장면을 이해할 수 있게 적어주세요."
                  />
                  <small>{(photo.altText ?? '').length}/500 · 입력을 멈추면 저장됩니다.</small>
                </label>
              </div>
            </li>
          ))}
        </ol>
      )}

      {saveState === 'error' && (
        <button
          className="draft-photo-retry"
          type="button"
          onClick={() => setRetryVersion((current) => current + 1)}
        >변경 내용 다시 저장</button>
      )}
    </section>
  );
}

function DraftPhotoThumbnail({ photo, position }: { photo: DraftPhoto; position: number }) {
  const [source, setSource] = useState<string | null>(null);

  useEffect(() => {
    if (!photo.thumbnailPath) return;
    let active = true;
    let objectUrl: string | null = null;
    getDraftPhotoThumbnail(photo.thumbnailPath)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setSource(objectUrl);
      })
      .catch(() => {
        if (active) setSource(null);
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photo.thumbnailPath]);

  return (
    <div className="draft-photo-preview">
      {source
        ? (
            <Image
              src={source}
              alt={photo.altText || `${position}번째 사진 미리보기`}
              fill
              sizes="(max-width: 640px) 100vw, 180px"
              unoptimized
            />
          )
        : <span aria-label={`${position}번째 사진 미리보기 준비 중`}>미리보기 준비 중</span>}
      {photo.cover && <b>대표</b>}
    </div>
  );
}

function PhotoSaveStatus({
  loadState,
  saveState,
}: {
  loadState: LoadState;
  saveState: SaveState;
}) {
  if (loadState === 'loading') return <span className="draft-photo-status">불러오는 중</span>;
  if (saveState === 'saving') return <span className="draft-photo-status">저장 중…</span>;
  if (saveState === 'error') return <span className="draft-photo-status is-error">저장 실패</span>;
  return <span className="draft-photo-status">자동 저장됨</span>;
}

function snapshot(photos: DraftPhoto[]): string {
  return JSON.stringify(toEditItems(photos));
}

function toEditItems(photos: DraftPhoto[]): DraftPhotoEditItem[] {
  return photos.map((photo) => ({
    photoId: photo.id,
    cover: photo.cover,
    altText: photo.altText?.trim() || null,
  }));
}
