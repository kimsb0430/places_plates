'use client';

import { useEffect, useState } from 'react';
import {
  DraftPostApiError,
  getPostPublicationReadiness,
  publishDraftPost,
} from '../api/draft-post-api';
import type {
  PostPublicationReadiness,
  PostPublicationResult,
  PostVisibility,
} from '../types';

interface DraftPublicationPanelProps {
  draftPostId: string;
  canPublish: boolean;
  readinessVersion: number;
  onUnauthorized: () => void;
}

type LoadState = 'loading' | 'ready' | 'error';

const VISIBILITY_OPTIONS: Array<{
  value: PostVisibility;
  title: string;
  description: string;
}> = [
  {
    value: 'PRIVATE',
    title: '비공개 보관',
    description: '나만 볼 수 있는 완성 기록으로 보관합니다.',
  },
  {
    value: 'UNLISTED',
    title: '링크 공개',
    description: '전체 목록에서는 숨기고 공유 링크로만 보여줍니다.',
  },
  {
    value: 'PUBLIC',
    title: '전체 공개',
    description: '공개 목록과 지도에서 누구나 발견할 수 있습니다.',
  },
];

export function DraftPublicationPanel({
  draftPostId,
  canPublish,
  readinessVersion,
  onUnauthorized,
}: DraftPublicationPanelProps) {
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [readiness, setReadiness] = useState<PostPublicationReadiness | null>(null);
  const [visibility, setVisibility] = useState<PostVisibility>('PRIVATE');
  const [message, setMessage] = useState('');
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [isPublishing, setIsPublishing] = useState(false);
  const [published, setPublished] = useState<PostPublicationResult | null>(null);

  useEffect(() => {
    if (published) return;
    const abortController = new AbortController();
    getPostPublicationReadiness(draftPostId, abortController.signal)
      .then((nextReadiness) => {
        setReadiness(nextReadiness);
        setLoadState('ready');
        setMessage('');
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) return;
        if (error instanceof DraftPostApiError && error.status === 401) {
          onUnauthorized();
          return;
        }
        setLoadState('error');
        setMessage(error instanceof DraftPostApiError
          ? error.message
          : '게시 전 검사 결과를 불러오지 못했습니다.');
      });
    return () => abortController.abort();
  }, [draftPostId, onUnauthorized, published, readinessVersion, refreshVersion]);

  const handlePublish = async () => {
    if (!readiness?.ready || !canPublish || isPublishing) return;
    setIsPublishing(true);
    setMessage('게시 전 안전 검사를 다시 확인하고 있습니다.');
    try {
      const result = await publishDraftPost(draftPostId, visibility);
      setPublished(result);
      setMessage('기록을 게시했습니다.');
    } catch (error: unknown) {
      if (error instanceof DraftPostApiError && error.status === 401) {
        onUnauthorized();
        return;
      }
      setMessage(error instanceof DraftPostApiError
        ? error.message
        : '기록을 게시하지 못했습니다.');
      setRefreshVersion((current) => current + 1);
    } finally {
      setIsPublishing(false);
    }
  };

  const handleRefresh = () => {
    setLoadState('loading');
    setReadiness(null);
    setRefreshVersion((current) => current + 1);
  };

  if (published) {
    const selected = VISIBILITY_OPTIONS.find((option) => option.value === published.visibility);
    return (
      <section className="publication-panel is-published" aria-labelledby="publication-heading">
        <p className="login-status">PUBLISHED</p>
        <h2 id="publication-heading">기록 게시 완료</h2>
        <p><strong>{selected?.title}</strong> 상태로 안전하게 게시했습니다.</p>
        <small>공개 목록과 상세 화면은 다음 공개 페이지 개발 단계에서 연결됩니다.</small>
      </section>
    );
  }

  return (
    <section className="publication-panel" aria-labelledby="publication-heading">
      <div className="publication-heading">
        <div>
          <p className="login-status">PUBLICATION</p>
          <h2 id="publication-heading">공개 범위와 게시 전 검사</h2>
          <p>저장된 입력과 보호 처리된 사진만 기준으로 최종 검사를 수행합니다.</p>
        </div>
        <button
          type="button"
          className="publication-refresh"
          disabled={loadState === 'loading'}
          onClick={handleRefresh}
        >다시 검사</button>
      </div>

      <fieldset className="publication-options">
        <legend>공개 범위</legend>
        {VISIBILITY_OPTIONS.map((option) => (
          <label key={option.value} className={visibility === option.value ? 'is-selected' : ''}>
            <input
              type="radio"
              name="visibility"
              value={option.value}
              checked={visibility === option.value}
              onChange={() => setVisibility(option.value)}
            />
            <span>
              <strong>{option.title}</strong>
              <small>{option.description}</small>
            </span>
          </label>
        ))}
      </fieldset>

      {loadState === 'loading' && <p className="publication-message">게시 조건을 검사하고 있습니다.</p>}
      {loadState === 'error' && <p className="publication-message is-error" role="alert">{message}</p>}
      {readiness && (
        <ul className="publication-checks" aria-label="게시 전 검사 결과">
          {readiness.checks.map((check) => (
            <li key={check.code} className={check.passed ? 'is-passed' : 'is-failed'}>
              <span aria-hidden="true">{check.passed ? '✓' : '!'}</span>
              {check.label}
            </li>
          ))}
        </ul>
      )}

      {!canPublish && (
        <p className="publication-message">자동 저장이 끝난 뒤 게시할 수 있습니다.</p>
      )}
      {message && loadState !== 'error' && (
        <p className="publication-message" aria-live="polite">{message}</p>
      )}
      <button
        type="button"
        className="publication-submit"
        disabled={!readiness?.ready || !canPublish || isPublishing}
        onClick={handlePublish}
      >{isPublishing ? '안전 검사 중…' : '이 범위로 게시하기'}</button>
    </section>
  );
}
