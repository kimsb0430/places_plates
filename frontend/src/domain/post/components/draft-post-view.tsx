'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  AuthenticationApiError,
  getAdministratorSession,
} from '@/domain/auth/api/authentication-api';
import { DraftPostApiError, getDraftPost } from '../api/draft-post-api';
import type { DraftPost } from '../types';

type DraftState =
  | { status: 'loading' }
  | { status: 'ready'; draft: DraftPost }
  | { status: 'unavailable'; message: string };

interface DraftPostViewProps {
  draftPostId: string;
}

export function DraftPostView({ draftPostId }: DraftPostViewProps) {
  const router = useRouter();
  const [state, setState] = useState<DraftState>({ status: 'loading' });

  useEffect(() => {
    let isActive = true;
    const nextPath = `/manage/drafts/${draftPostId}`;

    getAdministratorSession()
      .then(() => getDraftPost(draftPostId))
      .then((draft) => {
        if (isActive) setState({ status: 'ready', draft });
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        if (error instanceof AuthenticationApiError && error.status === 401) {
          router.replace(`/login?next=${encodeURIComponent(nextPath)}`);
          return;
        }
        setState({
          status: 'unavailable',
          message: error instanceof AuthenticationApiError || error instanceof DraftPostApiError
            ? error.message
            : '초안을 불러오지 못했습니다.',
        });
      });

    return () => {
      isActive = false;
    };
  }, [draftPostId, router]);

  if (state.status === 'loading') {
    return (
      <section className="manage-gate" aria-live="polite">
        <p className="login-status">PRIVATE DRAFT</p>
        <h1>업로드한 기록을 준비하고 있습니다.</h1>
        <p>비공개 초안과 사진 업로드 상태를 확인하고 있습니다.</p>
      </section>
    );
  }

  if (state.status === 'unavailable') {
    return (
      <section className="manage-gate" role="alert">
        <p className="login-status">확인 필요</p>
        <h1>초안을 열지 못했습니다.</h1>
        <p>{state.message}</p>
        <Link className="draft-back-link" href="/manage">관리 화면으로 돌아가기</Link>
      </section>
    );
  }

  const { draft } = state;
  return (
    <article className="draft-detail">
      <div className="draft-detail-copy">
        <p className="login-status">PRIVATE DRAFT</p>
        <p className="overline">{draft.category === 'RESTAURANT' ? '맛집 기록' : '여행지 기록'}</p>
        <h1>{draft.title}</h1>
        <p className="draft-detail-lead">
          사진 업로드가 완료됐으며 이 기록은 공개되지 않는 비공개 초안으로 저장되었습니다.
        </p>
        <dl>
          <div><dt>공개 상태</dt><dd>비공개</dd></div>
          <div><dt>작성 상태</dt><dd>초안</dd></div>
          <div>
            <dt>저장 시각</dt>
            <dd>{new Intl.DateTimeFormat('ko-KR', {
              dateStyle: 'medium',
              timeStyle: 'short',
            }).format(new Date(draft.updatedAt))}</dd>
          </div>
        </dl>
        <div className="draft-detail-actions">
          <Link href="/manage">관리 화면으로 돌아가기</Link>
        </div>
      </div>
      <aside>
        <p className="login-status">NEXT STEP</p>
        <h2>기록 정보 입력</h2>
        <p>장소명, 방문 월, 지도 위치, 글 내용을 편집하는 화면은 다음 개발 단계에서 이 초안에 연결됩니다.</p>
        <strong>현재 사진과 초안은 안전하게 연결되어 있습니다.</strong>
      </aside>
    </article>
  );
}
