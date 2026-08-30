import type {
  DraftPost,
  DraftPostUpdateInput,
  PlaceConnectionInput,
  PlaceSearchResult,
  PostPublicationReadiness,
  PostPublicationResult,
  PostVisibility,
} from '../types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

interface ApiErrorResponse {
  code?: string;
  message?: string;
}

interface CsrfTokenResponse {
  headerName: string;
  token: string;
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...init?.headers,
      },
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

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function getDraftPosts(): Promise<DraftPost[]> {
  return request<DraftPost[]>('/api/v1/manage/drafts');
}

export function getDraftPost(draftPostId: string): Promise<DraftPost> {
  return request<DraftPost>(`/api/v1/manage/drafts/${draftPostId}`);
}

export async function deleteDraftPost(draftPostId: string): Promise<void> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf');
  return request<void>(`/api/v1/manage/drafts/${draftPostId}`, {
    method: 'DELETE',
    headers: { [csrfToken.headerName]: csrfToken.token },
  });
}

export function getManagedPublishedPosts(): Promise<DraftPost[]> {
  return request<DraftPost[]>('/api/v1/manage/posts');
}

export async function deleteManagedPublishedPost(postId: string): Promise<void> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf');
  return request<void>(`/api/v1/manage/posts/${postId}`, {
    method: 'DELETE',
    headers: { [csrfToken.headerName]: csrfToken.token },
  });
}

export async function updateDraftPost(
  draftPostId: string,
  input: DraftPostUpdateInput,
  signal?: AbortSignal,
): Promise<DraftPost> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf', { signal });
  return request<DraftPost>(`/api/v1/manage/drafts/${draftPostId}`, {
    method: 'PATCH',
    signal,
    headers: {
      'Content-Type': 'application/json',
      [csrfToken.headerName]: csrfToken.token,
    },
    body: JSON.stringify(input),
  });
}

export function searchPlaces(query: string, signal?: AbortSignal): Promise<PlaceSearchResult[]> {
  const parameters = new URLSearchParams({ query });
  return request<PlaceSearchResult[]>(`/api/v1/manage/places/search?${parameters}`, { signal });
}

export async function connectDraftPlace(
  draftPostId: string,
  input: PlaceConnectionInput,
): Promise<DraftPost> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf');
  return request<DraftPost>(`/api/v1/manage/drafts/${draftPostId}/place`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      [csrfToken.headerName]: csrfToken.token,
    },
    body: JSON.stringify(input),
  });
}

export async function disconnectDraftPlace(draftPostId: string): Promise<DraftPost> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf');
  return request<DraftPost>(`/api/v1/manage/drafts/${draftPostId}/place`, {
    method: 'DELETE',
    headers: { [csrfToken.headerName]: csrfToken.token },
  });
}

export function getPostPublicationReadiness(
  draftPostId: string,
  signal?: AbortSignal,
): Promise<PostPublicationReadiness> {
  return request<PostPublicationReadiness>(
    `/api/v1/manage/drafts/${draftPostId}/publication-readiness`,
    { signal },
  );
}

export async function publishDraftPost(
  draftPostId: string,
  visibility: PostVisibility,
): Promise<PostPublicationResult> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf');
  return request<PostPublicationResult>(`/api/v1/manage/drafts/${draftPostId}/publication`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrfToken.headerName]: csrfToken.token,
    },
    body: JSON.stringify({ visibility }),
  });
}
