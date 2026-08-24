import type { PostCategory } from '@/domain/photo/types';

export interface DraftPost {
  id: string;
  category: PostCategory;
  title: string;
  visibility: 'PRIVATE';
  status: 'DRAFT';
  createdAt: string;
  updatedAt: string;
}
