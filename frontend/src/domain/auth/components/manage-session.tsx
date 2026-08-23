'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  AuthenticationApiError,
  getAdministratorSession,
  logoutAdministrator,
} from '../api/authentication-api';
import type { AdministratorSession } from '../types';

type SessionState =
  | { status: 'loading' }
  | { status: 'authenticated'; session: AdministratorSession }
  | { status: 'unavailable'; message: string };

export function ManageSession() {
  const router = useRouter();
  const [sessionState, setSessionState] = useState<SessionState>({ status: 'loading' });
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const restoreSession = useCallback(async () => {
    try {
      const session = await getAdministratorSession();
      setSessionState({ status: 'authenticated', session });
    } catch (error) {
      if (error instanceof AuthenticationApiError && error.status === 401) {
        router.replace('/login?next=/manage');
        return;
      }
      setSessionState({
        status: 'unavailable',
        message: error instanceof AuthenticationApiError
          ? error.message
          : '세션을 확인할 수 없습니다.',
      });
    }
  }, [router]);

  useEffect(() => {
    let isActive = true;

    getAdministratorSession()
      .then((session) => {
        if (isActive) {
          setSessionState({ status: 'authenticated', session });
        }
      })
      .catch((error: unknown) => {
        if (!isActive) {
          return;
        }
        if (error instanceof AuthenticationApiError && error.status === 401) {
          router.replace('/login?next=/manage');
          return;
        }
        setSessionState({
          status: 'unavailable',
          message: error instanceof AuthenticationApiError
            ? error.message
            : '세션을 확인할 수 없습니다.',
        });
      });

    return () => {
      isActive = false;
    };
  }, [router]);

  async function handleLogout() {
    setIsLoggingOut(true);

    try {
      await logoutAdministrator();
      router.replace('/login');
    } catch (error) {
      setSessionState({
        status: 'unavailable',
        message: error instanceof AuthenticationApiError
          ? error.message
          : '로그아웃 처리 중 문제가 발생했습니다.',
      });
      setIsLoggingOut(false);
    }
  }

  function handleRetry() {
    setSessionState({ status: 'loading' });
    void restoreSession();
  }

  if (sessionState.status === 'loading') {
    return (
      <section className="manage-gate" aria-live="polite">
        <p className="login-status">세션 확인 중</p>
        <h1>관리 공간을 준비하고 있습니다.</h1>
        <p>보호된 세션을 확인한 뒤 기록 관리 기능을 표시합니다.</p>
      </section>
    );
  }

  if (sessionState.status === 'unavailable') {
    return (
      <section className="manage-gate" role="alert">
        <p className="login-status">연결 확인 필요</p>
        <h1>세션을 확인하지 못했습니다.</h1>
        <p>{sessionState.message}</p>
        <button type="button" onClick={handleRetry}>다시 확인하기</button>
      </section>
    );
  }

  return (
    <section className="manage-dashboard" aria-labelledby="manage-title">
      <div>
        <p className="overline">PRIVATE ARCHIVE</p>
        <h1 id="manage-title">기록 관리</h1>
        <p>{sessionState.session.email} 계정의 보호된 관리 공간입니다.</p>
      </div>
      <div className="manage-placeholder">
        <p className="login-status">관리자 세션 활성</p>
        <h2>로그인이 안전하게 연결되었습니다.</h2>
        <p>게시물 작성과 사진 관리는 다음 개발 단계에서 이 공간에 추가됩니다.</p>
        <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
          {isLoggingOut ? '로그아웃 중…' : '로그아웃'}
        </button>
      </div>
    </section>
  );
}
