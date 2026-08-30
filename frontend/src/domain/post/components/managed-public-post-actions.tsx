'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  AuthenticationApiError,
  getAdministratorSession,
} from '@/domain/auth/api/authentication-api';
import {
  deleteManagedPublishedPost,
  DraftPostApiError,
} from '../api/draft-post-api';

interface ManagedPublicPostActionsProps {
  postId: string;
  title: string;
}

type OwnerActionState = 'checking' | 'hidden' | 'ready' | 'deleting';

export function ManagedPublicPostActions({ postId, title }: ManagedPublicPostActionsProps) {
  const router = useRouter();
  const [state, setState] = useState<OwnerActionState>('checking');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let isActive = true;

    getAdministratorSession()
      .then(() => {
        if (isActive) setState('ready');
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        if (error instanceof AuthenticationApiError && error.status === 401) {
          setState('hidden');
          return;
        }
        setState('hidden');
      });

    return () => {
      isActive = false;
    };
  }, []);

  async function handleDelete() {
    if (!window.confirm(`“${title}” 공개 기록을 영구 삭제할까요? 공개 페이지와 지도에서 사라지고 사진도 함께 삭제됩니다.`)) {
      return;
    }

    setState('deleting');
    setErrorMessage(null);

    try {
      await deleteManagedPublishedPost(postId);
      router.replace('/manage');
      router.refresh();
    } catch (error: unknown) {
      setErrorMessage(error instanceof DraftPostApiError ? error.message : '공개 기록을 삭제하지 못했습니다.');
      setState('ready');
    }
  }

  if (state === 'checking' || state === 'hidden') return null;

  return (
    <aside className="public-detail-owner-actions" aria-label="공개 기록 관리">
      <div>
        <strong>관리자 전용</strong>
        <span>이 공개 기록을 관리하거나 영구 삭제할 수 있습니다.</span>
      </div>
      <div>
        <Link href="/manage">관리 화면</Link>
        <button type="button" disabled={state === 'deleting'} onClick={() => void handleDelete()}>
          {state === 'deleting' ? '삭제 중…' : '이 기록 삭제'}
        </button>
      </div>
      {errorMessage && <p role="alert">{errorMessage}</p>}
    </aside>
  );
}
