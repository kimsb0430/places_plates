import type {
  PostCategory,
  PublicPlaceHistory,
  PublicPostDetail,
  PublicPostList,
  PublicPostSort,
} from '../types';

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || 'http://localhost:8080'
).replace(/\/$/, '');

export class PublicPostApiError extends Error {
  constructor(
    message: string,
    public readonly status: number | null = null,
    public readonly code: string | null = null,
  ) {
    super(message);
    this.name = 'PublicPostApiError';
  }
}

export async function getPublicPosts(
  category: PostCategory | undefined,
  sort: PublicPostSort,
): Promise<PublicPostList> {
  const parameters = new URLSearchParams();
  if (category) parameters.set('category', category);
  parameters.set('sort', sort);
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/api/v1/public/posts?${parameters.toString()}`, {
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });
  } catch {
    throw new PublicPostApiError('공개 기록 서버에 연결할 수 없습니다.');
  }

  if (!response.ok) {
    throw await createApiError(response, '공개 기록을 불러오지 못했습니다.');
  }

  return response.json() as Promise<PublicPostList>;
}

export async function getPublicPost(postId: string): Promise<PublicPostDetail> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/api/v1/public/posts/${postId}`, {
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });
  } catch {
    throw new PublicPostApiError('공개 기록 서버에 연결할 수 없습니다.');
  }

  if (!response.ok) {
    throw await createApiError(response, '공개 기록을 불러오지 못했습니다.');
  }

  return response.json() as Promise<PublicPostDetail>;
}

export async function getPublicPlaceHistory(postId: string): Promise<PublicPlaceHistory> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/api/v1/public/posts/${postId}/place`, {
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });
  } catch {
    throw new PublicPostApiError('공개 장소 기록 서버에 연결할 수 없습니다.');
  }

  if (!response.ok) {
    throw await createApiError(response, '공개 장소 방문 기록을 불러오지 못했습니다.');
  }

  return response.json() as Promise<PublicPlaceHistory>;
}

export function getPublicCoverUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

export function getPublicPhotoUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

async function createApiError(response: Response, fallbackMessage: string): Promise<PublicPostApiError> {
  const body = await response.json().catch(() => null) as { code?: string; message?: string } | null;
  return new PublicPostApiError(
    body?.message ?? fallbackMessage,
    response.status,
    body?.code ?? null,
  );
}
