import type { PostCategory } from '@/domain/photo/types';
import type { MapPostList } from '../types';

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || 'http://localhost:8080'
).replace(/\/$/, '');

export class PublicMapApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PublicMapApiError';
  }
}

export async function getMapPosts(category?: PostCategory): Promise<MapPostList> {
  const parameters = new URLSearchParams();
  if (category) parameters.set('category', category);
  const query = parameters.toString();

  try {
    const response = await fetch(
      `${API_BASE_URL}/api/v1/map/posts${query ? `?${query}` : ''}`,
      {
        cache: 'no-store',
        headers: { Accept: 'application/json' },
      },
    );
    if (!response.ok) {
      throw new PublicMapApiError('지도 기록을 불러오지 못했습니다.');
    }
    return response.json() as Promise<MapPostList>;
  } catch (error: unknown) {
    if (error instanceof PublicMapApiError) throw error;
    throw new PublicMapApiError('지도 기록 서버에 연결할 수 없습니다.');
  }
}
