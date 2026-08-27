import type { PostCategory, PublicPostList, PublicPostSort } from '../types';

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || 'http://localhost:8080'
).replace(/\/$/, '');

export class PublicPostApiError extends Error {
  constructor(message: string) {
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
    throw new PublicPostApiError('공개 기록을 불러오지 못했습니다.');
  }

  return response.json() as Promise<PublicPostList>;
}

export function getPublicCoverUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}
