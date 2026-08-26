import type { PostCategory } from '@/domain/photo/types';

export interface DraftPost {
  id: string;
  category: PostCategory;
  title: string;
  summary: string | null;
  content: string | null;
  publicVisitYear: number | null;
  publicVisitMonth: number | null;
  visibility: 'PRIVATE';
  status: 'DRAFT';
  createdAt: string;
  updatedAt: string;
}

export interface DraftPostUpdateInput {
  title: string;
  summary: string | null;
  content: string | null;
  publicVisitYear: number | null;
  publicVisitMonth: number | null;
}
