import type { DraftPhoto, DraftPhotoEditItem } from '../types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

interface ApiErrorResponse {
  code?: string;
  message?: string;
}

interface CsrfTokenResponse {
  headerName: string;
  token: string;
}

export class DraftPhotoApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'DraftPhotoApiError';
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
    throw new DraftPhotoApiError(
      0,
      'DRAFT_PHOTO_SERVER_UNAVAILABLE',
      '사진 편집 서버에 연결할 수 없습니다.',
    );
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({})) as ApiErrorResponse;
    throw new DraftPhotoApiError(
      response.status,
      errorBody.code ?? 'DRAFT_PHOTO_UNKNOWN_ERROR',
      errorBody.message ?? '사진 정보를 처리하지 못했습니다.',
    );
  }
  return response.json() as Promise<T>;
}

export function getDraftPhotos(draftPostId: string): Promise<DraftPhoto[]> {
  return request<DraftPhoto[]>(`/api/v1/manage/drafts/${draftPostId}/photos`);
}

export async function updateDraftPhotos(
  draftPostId: string,
  photos: DraftPhotoEditItem[],
  signal?: AbortSignal,
): Promise<DraftPhoto[]> {
  const csrf = await request<CsrfTokenResponse>('/api/v1/auth/csrf', { signal });
  return request<DraftPhoto[]>(`/api/v1/manage/drafts/${draftPostId}/photos`, {
    method: 'PUT',
    signal,
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ photos }),
  });
}

export async function getDraftPhotoThumbnail(thumbnailPath: string): Promise<Blob> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${thumbnailPath}`, {
      credentials: 'include',
      headers: { Accept: 'image/*' },
    });
  } catch {
    throw new DraftPhotoApiError(
      0,
      'DRAFT_PHOTO_SERVER_UNAVAILABLE',
      '사진 미리보기에 연결할 수 없습니다.',
    );
  }
  if (!response.ok) {
    throw new DraftPhotoApiError(
      response.status,
      'DRAFT_PHOTO_THUMBNAIL_UNAVAILABLE',
      '사진 미리보기를 불러오지 못했습니다.',
    );
  }
  return response.blob();
}
