'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { DraftPostApiError, getDraftPosts } from '../api/draft-post-api';
import type { DraftPost } from '../types';

type DraftListState =
  | { status: 'loading' }
  | { status: 'ready'; drafts: DraftPost[] }
  | { status: 'unavailable'; message: string };

export function DraftList() {
  const [state, setState] = useState<DraftListState>({ status: 'loading' });

  useEffect(() => {
    let isActive = true;

    getDraftPosts()
      .then((drafts) => {
        if (isActive) setState({ status: 'ready', drafts });
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        setState({
          status: 'unavailable',
          message: error instanceof DraftPostApiError
            ? error.message
            : '초안 목록을 불러오지 못했습니다.',
        });
      });

    return () => {
      isActive = false;
    };
  }, []);

  return (
    <section className="draft-list" aria-labelledby="draft-list-title">
      <div className="draft-list-heading">
        <div>
          <p className="login-status">PRIVATE DRAFTS</p>
          <h2 id="draft-list-title">작성 중인 초안</h2>
        </div>
        {state.status === 'ready' && <strong>{state.drafts.length}</strong>}
      </div>

      {state.status === 'loading' && <p className="draft-list-message">초안을 불러오는 중입니다.</p>}
      {state.status === 'unavailable' && (
        <p className="draft-list-message" role="alert">{state.message}</p>
      )}
      {state.status === 'ready' && state.drafts.length === 0 && (
        <p className="draft-list-message">아직 작성 중인 초안이 없습니다.</p>
      )}
      {state.status === 'ready' && state.drafts.length > 0 && (
        <ul>
          {state.drafts.map((draft) => (
            <li key={draft.id}>
              <Link href={`/manage/drafts/${draft.id}`}>
                <span>{categoryLabel(draft.category)}</span>
                <strong>{draft.title}</strong>
                <small>
                  {new Intl.DateTimeFormat('ko-KR', {
                    dateStyle: 'medium',
                    timeStyle: 'short',
                  }).format(new Date(draft.updatedAt))}
                </small>
                <i aria-hidden="true">→</i>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function categoryLabel(category: DraftPost['category']): string {
  return category === 'RESTAURANT' ? '맛집' : '여행지';
}
