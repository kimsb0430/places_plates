'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AuthenticationApiError,
  getAdministratorSession,
} from '@/domain/auth/api/authentication-api';
import {
  DraftPostApiError,
  getDraftPost,
  updateDraftPost,
} from '../api/draft-post-api';
import type { DraftPost, DraftPostUpdateInput } from '../types';
import {
  DestinationDetailFields,
  type DestinationEditorValues,
} from './destination-detail-fields';
import {
  RestaurantDetailFields,
  type RestaurantEditorValues,
} from './restaurant-detail-fields';
import { PlaceFields } from './place-fields';

type DraftState =
  | { status: 'loading' }
  | { status: 'ready'; draft: DraftPost }
  | { status: 'unavailable'; message: string };

type SaveState =
  | { status: 'saved' }
  | { status: 'saving' }
  | { status: 'incomplete' }
  | { status: 'error'; message: string };

interface DraftEditorForm {
  title: string;
  visitMonth: string;
  summary: string;
  content: string;
  restaurant: RestaurantEditorValues;
  destination: DestinationEditorValues;
}

interface DraftPostViewProps {
  draftPostId: string;
}

interface DraftPostEditorProps {
  initialDraft: DraftPost;
}

const AUTOSAVE_DELAY_MS = 700;

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
        <h1>작성 중인 기록을 불러오고 있습니다.</h1>
        <p>저장된 제목과 방문 기록을 안전하게 확인하고 있습니다.</p>
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

  return <DraftPostEditor initialDraft={state.draft} />;
}

function DraftPostEditor({ initialDraft }: DraftPostEditorProps) {
  const router = useRouter();
  const [draft, setDraft] = useState(initialDraft);
  const [form, setForm] = useState<DraftEditorForm>(() => toEditorForm(initialDraft));
  const [saveState, setSaveState] = useState<SaveState>({ status: 'saved' });
  const [retryVersion, setRetryVersion] = useState(0);
  const lastSavedSnapshot = useRef(JSON.stringify(toEditorForm(initialDraft)));
  const completionCount = useMemo(() => [
    form.title.trim().length > 0,
    form.visitMonth.length > 0,
    form.summary.trim().length > 0,
  ].filter(Boolean).length, [form.summary, form.title, form.visitMonth]);

  useEffect(() => {
    const snapshot = JSON.stringify(form);
    if (snapshot === lastSavedSnapshot.current) return;
    if (!form.title.trim()) return;

    const abortController = new AbortController();
    const timer = window.setTimeout(() => {
      setSaveState({ status: 'saving' });
      updateDraftPost(
        initialDraft.id,
        toUpdateInput(form, initialDraft.category),
        abortController.signal,
      )
        .then((savedDraft) => {
          lastSavedSnapshot.current = snapshot;
          setDraft(savedDraft);
          setSaveState({ status: 'saved' });
        })
        .catch((error: unknown) => {
          if (abortController.signal.aborted) return;
          if (error instanceof DraftPostApiError && error.status === 401) {
            router.replace(`/login?next=${encodeURIComponent(`/manage/drafts/${initialDraft.id}`)}`);
            return;
          }
          setSaveState({
            status: 'error',
            message: error instanceof DraftPostApiError
              ? error.message
              : '자동 저장에 실패했습니다.',
          });
        });
    }, AUTOSAVE_DELAY_MS);

    return () => {
      window.clearTimeout(timer);
      abortController.abort();
    };
  }, [form, initialDraft.category, initialDraft.id, retryVersion, router]);

  const handleFieldChange = (field: keyof DraftEditorForm, value: string) => {
    if (field === 'title' && !value.trim()) {
      setSaveState({ status: 'incomplete' });
    }
    setForm((current) => ({ ...current, [field]: value }));
  };

  const handleRestaurantFieldChange = (
    field: keyof RestaurantEditorValues,
    value: string,
  ) => {
    setForm((current) => ({
      ...current,
      restaurant: { ...current.restaurant, [field]: value },
    }));
  };

  const handleDestinationFieldChange = (
    field: keyof DestinationEditorValues,
    value: string,
  ) => {
    setForm((current) => ({
      ...current,
      destination: { ...current.destination, [field]: value },
    }));
  };

  return (
    <article className="draft-editor">
      <section className="draft-editor-form">
        <div className="draft-editor-heading">
          <div>
            <p className="login-status">PRIVATE DRAFT</p>
            <p className="overline">
              {draft.category === 'RESTAURANT' ? '맛집 기록' : '여행지 기록'}
            </p>
            <h1>기록 정보 편집</h1>
          </div>
          <AutosaveStatus state={saveState} updatedAt={draft.updatedAt} />
        </div>

        <div className="draft-editor-fields">
          <label htmlFor="draft-title">
            <span>제목 <b>필수</b></span>
            <input
              id="draft-title"
              name="title"
              value={form.title}
              maxLength={200}
              required
              onChange={(event) => handleFieldChange('title', event.target.value)}
              placeholder="기억하고 싶은 장면을 제목으로 남겨보세요"
            />
            <small>{form.title.length}/200</small>
          </label>

          <label htmlFor="draft-visit-month">
            <span>방문 월 <b>필수</b></span>
            <input
              id="draft-visit-month"
              name="visitMonth"
              type="month"
              value={form.visitMonth}
              required
              onChange={(event) => handleFieldChange('visitMonth', event.target.value)}
            />
            <small>공개 페이지에는 일자를 제외한 월 단위로 표시됩니다.</small>
          </label>

          <label htmlFor="draft-summary">
            <span>한줄평 <b>필수</b></span>
            <input
              id="draft-summary"
              name="summary"
              value={form.summary}
              maxLength={500}
              required
              onChange={(event) => handleFieldChange('summary', event.target.value)}
              placeholder="이 장소를 한 문장으로 기억한다면"
            />
            <small>{form.summary.length}/500</small>
          </label>

          <label htmlFor="draft-content">
            <span>본문 <em>선택</em></span>
            <textarea
              id="draft-content"
              name="content"
              value={form.content}
              maxLength={50000}
              rows={10}
              onChange={(event) => handleFieldChange('content', event.target.value)}
              placeholder="메뉴, 동선, 분위기처럼 나중에도 떠올리고 싶은 내용을 자유롭게 적어보세요."
            />
            <small>{form.content.length.toLocaleString('ko-KR')}/50,000</small>
          </label>
        </div>

        <PlaceFields
          draftPostId={draft.id}
          value={draft.place}
          onSaved={setDraft}
          onUnauthorized={() => router.replace(
            `/login?next=${encodeURIComponent(`/manage/drafts/${initialDraft.id}`)}`,
          )}
        />

        {draft.category === 'RESTAURANT' && (
          <RestaurantDetailFields
            value={form.restaurant}
            onChange={handleRestaurantFieldChange}
          />
        )}

        {draft.category === 'DESTINATION' && (
          <DestinationDetailFields
            value={form.destination}
            onChange={handleDestinationFieldChange}
          />
        )}

        <div className="draft-editor-actions">
          <Link href="/manage">관리 화면으로 돌아가기</Link>
          {saveState.status === 'error' && (
            <button type="button" onClick={() => setRetryVersion((value) => value + 1)}>
              다시 저장
            </button>
          )}
        </div>
      </section>

      <aside className="draft-editor-guide">
        <p className="login-status">AUTOSAVE</p>
        <h2>{completionCount}/3 작성 완료</h2>
        <p>입력을 멈추면 자동으로 저장됩니다. 다른 기기에서도 로그인하면 이어서 작성할 수 있습니다.</p>
        <ul>
          <li className={form.title.trim() ? 'is-complete' : ''}>기록 제목</li>
          <li className={form.visitMonth ? 'is-complete' : ''}>방문 월</li>
          <li className={form.summary.trim() ? 'is-complete' : ''}>한줄평</li>
        </ul>
        <strong>사진과 입력 내용은 게시하기 전까지 비공개로 유지됩니다.</strong>
      </aside>
    </article>
  );
}

function AutosaveStatus({ state, updatedAt }: { state: SaveState; updatedAt: string }) {
  if (state.status === 'saving') {
    return <p className="draft-save-status is-saving" aria-live="polite">저장 중…</p>;
  }
  if (state.status === 'incomplete') {
    return <p className="draft-save-status is-warning" aria-live="polite">제목을 입력하면 저장됩니다.</p>;
  }
  if (state.status === 'error') {
    return <p className="draft-save-status is-error" role="alert">{state.message}</p>;
  }
  return (
    <p className="draft-save-status" aria-live="polite">
      자동 저장됨 · <time dateTime={updatedAt}>{formatSavedAt(updatedAt)}</time>
    </p>
  );
}

function toEditorForm(draft: DraftPost): DraftEditorForm {
  return {
    title: draft.title,
    visitMonth: draft.publicVisitYear && draft.publicVisitMonth
      ? `${draft.publicVisitYear}-${String(draft.publicVisitMonth).padStart(2, '0')}`
      : '',
    summary: draft.summary ?? '',
    content: draft.content ?? '',
    restaurant: {
      rating: draft.restaurantDetails?.rating?.toFixed(1) ?? '',
      recommendedMenu: draft.restaurantDetails?.recommendedMenu ?? '',
      priceRange: draft.restaurantDetails?.priceRange ?? '',
      waitingMinutes: draft.restaurantDetails?.waitingMinutes?.toString() ?? '',
      revisitIntention: draft.restaurantDetails?.revisitIntention ?? '',
    },
    destination: {
      recommendedTime: draft.destinationDetails?.recommendedTime ?? '',
      durationMinutes: draft.destinationDetails?.durationMinutes?.toString() ?? '',
      highlights: draft.destinationDetails?.highlights ?? '',
      travelTips: draft.destinationDetails?.travelTips ?? '',
    },
  };
}

function toUpdateInput(
  form: DraftEditorForm,
  category: DraftPost['category'],
): DraftPostUpdateInput {
  const [year, month] = form.visitMonth
    ? form.visitMonth.split('-').map(Number)
    : [null, null];
  return {
    title: form.title,
    summary: form.summary.trim() || null,
    content: form.content.trim() || null,
    publicVisitYear: year,
    publicVisitMonth: month,
    restaurantDetails: category === 'RESTAURANT'
      ? {
          rating: form.restaurant.rating ? Number(form.restaurant.rating) : null,
          recommendedMenu: form.restaurant.recommendedMenu.trim() || null,
          priceRange: form.restaurant.priceRange || null,
          waitingMinutes: form.restaurant.waitingMinutes
            ? Number(form.restaurant.waitingMinutes)
            : null,
          revisitIntention: form.restaurant.revisitIntention || null,
        }
      : null,
    destinationDetails: category === 'DESTINATION'
      ? {
          recommendedTime: form.destination.recommendedTime.trim() || null,
          durationMinutes: form.destination.durationMinutes
            ? Number(form.destination.durationMinutes)
            : null,
          highlights: form.destination.highlights.trim() || null,
          travelTips: form.destination.travelTips.trim() || null,
        }
      : null,
  };
}

function formatSavedAt(updatedAt: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(updatedAt));
}
