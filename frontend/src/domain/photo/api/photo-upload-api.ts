import type {
  ImageSanitizationResult,
  UploadBatch,
  UploadFileDescriptor,
  UploadItem,
  PostCategory,
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

export class PhotoUploadApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'PhotoUploadApiError';
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
    throw new PhotoUploadApiError(
      0,
      'PHOTO_UPLOAD_SERVER_UNAVAILABLE',
      '사진 업로드 서버에 연결할 수 없습니다.',
    );
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({})) as ApiErrorResponse;
    throw new PhotoUploadApiError(
      response.status,
      errorBody.code ?? 'PHOTO_UPLOAD_UNKNOWN_ERROR',
      errorBody.message ?? '사진 업로드 요청을 처리하지 못했습니다.',
    );
  }

  return response.json() as Promise<T>;
}

async function mutate<T>(path: string, body?: unknown): Promise<T> {
  const csrfToken = await request<CsrfTokenResponse>('/api/v1/auth/csrf');
  return request<T>(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrfToken.headerName]: csrfToken.token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export function createUploadBatch(
  category: PostCategory,
  files: UploadFileDescriptor[],
  targetPostId?: string,
): Promise<UploadBatch> {
  return mutate<UploadBatch>('/api/v1/manage/photo-uploads', { category, targetPostId, files });
}

export function getUploadBatch(batchId: string): Promise<UploadBatch> {
  return request<UploadBatch>(`/api/v1/manage/photo-uploads/${batchId}`);
}

export function recordUploadProgress(
  batchId: string,
  itemId: string,
  uploadedBytes: number,
): Promise<UploadItem> {
  return mutate<UploadItem>(
    `/api/v1/manage/photo-uploads/${batchId}/items/${itemId}/progress`,
    { uploadedBytes },
  );
}

export function reportUploadFailure(
  batchId: string,
  itemId: string,
  failureCode: string,
): Promise<UploadItem> {
  return mutate<UploadItem>(
    `/api/v1/manage/photo-uploads/${batchId}/items/${itemId}/failure`,
    { failureCode },
  );
}

export function retryUpload(batchId: string, itemId: string): Promise<UploadItem> {
  return mutate<UploadItem>(
    `/api/v1/manage/photo-uploads/${batchId}/items/${itemId}/retry`,
  );
}

export function completeUpload(batchId: string, itemId: string): Promise<UploadItem> {
  return mutate<UploadItem>(
    `/api/v1/manage/photo-uploads/${batchId}/items/${itemId}/complete`,
  );
}

export function sanitizeUpload(
  batchId: string,
  itemId: string,
): Promise<ImageSanitizationResult> {
  return mutate<ImageSanitizationResult>(
    `/api/v1/manage/photo-uploads/${batchId}/items/${itemId}/sanitize`,
  );
}
