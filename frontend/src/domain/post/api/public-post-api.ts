import type { PostCategory, PublicPostList } from '../types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export class PublicPostApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PublicPostApiError';
  }
}

export async function getPublicPosts(category?: PostCategory): Promise<PublicPostList> {
  const parameters = category ? `?category=${category}` : '';
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/api/v1/public/posts${parameters}`, {
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
