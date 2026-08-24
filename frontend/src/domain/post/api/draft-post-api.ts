import type { DraftPost } from '../types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

interface ApiErrorResponse {
  code?: string;
  message?: string;
}

export class DraftPostApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'DraftPostApiError';
  }
}

async function request<T>(path: string): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
  } catch {
    throw new DraftPostApiError(
      0,
      'DRAFT_POST_SERVER_UNAVAILABLE',
      '초안 서버에 연결할 수 없습니다.',
    );
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({})) as ApiErrorResponse;
    throw new DraftPostApiError(
      response.status,
      errorBody.code ?? 'DRAFT_POST_UNKNOWN_ERROR',
      errorBody.message ?? '초안을 불러오지 못했습니다.',
    );
  }

  return response.json() as Promise<T>;
}

export function getDraftPosts(): Promise<DraftPost[]> {
  return request<DraftPost[]>('/api/v1/manage/drafts');
}

export function getDraftPost(draftPostId: string): Promise<DraftPost> {
  return request<DraftPost>(`/api/v1/manage/drafts/${draftPostId}`);
}
