'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useRef, useState } from 'react';
import { Upload } from 'tus-js-client';
import {
  completeUpload,
  createUploadBatch,
  PhotoUploadApiError,
  recordUploadProgress,
  reportUploadFailure,
  retryUpload,
  sanitizeUpload,
} from '../api/photo-upload-api';
import type { PostCategory, UploadItem, UploadTicket } from '../types';

const MAX_FILE_COUNT = 100;
const MAX_FILE_SIZE = 30 * 1024 * 1024;
const CHUNK_SIZE = 6 * 1024 * 1024;
const ACCEPTED_MIME_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/heic',
  'image/heif',
]);

type ClientUploadStatus = UploadItem['status'] | 'PAUSED' | 'SANITIZED' | 'PROCESSING_FAILED';

interface ClientUpload {
  id: string;
  file: File;
  progress: number;
  status: ClientUploadStatus;
  attemptCount: number;
  errorMessage?: string;
  variantCount?: number;
  ticket?: UploadTicket;
}

export function PhotoUploader() {
  const router = useRouter();
  const [category, setCategory] = useState<PostCategory>();
  const [batchId, setBatchId] = useState<string>();
  const [draftPostId, setDraftPostId] = useState<string>();
  const [expiresAt, setExpiresAt] = useState<string>();
  const [uploads, setUploads] = useState<ClientUpload[]>([]);
  const [isPreparing, setIsPreparing] = useState(false);
  const [message, setMessage] = useState<string>();
  const uploadInstances = useRef(new Map<string, Upload>());

  async function handleFiles(files: File[]) {
    setMessage(undefined);
    if (!category) {
      setMessage('먼저 맛집 또는 여행지 중 기록 종류를 선택해주세요.');
      return;
    }
    const validationMessage = validateFiles(files);
    if (validationMessage) {
      setMessage(validationMessage);
      return;
    }

    setIsPreparing(true);
    try {
      const batch = await createUploadBatch(category, files.map((file) => ({
        clientFileName: file.name,
        mimeType: file.type || inferMimeType(file.name),
        byteSize: file.size,
      })));
      setBatchId(batch.id);
      setDraftPostId(batch.draftPostId);
      setExpiresAt(batch.expiresAt);
      const nextUploads = batch.items.map((item, index): ClientUpload => ({
        id: item.id,
        file: files[index],
        progress: 0,
        status: item.status,
        attemptCount: item.attemptCount,
        ticket: item.uploadTicket ?? undefined,
      }));
      setUploads(nextUploads);
      const results = await runWithConcurrency(
        nextUploads,
        3,
        (upload) => startUpload(batch.id, upload),
      );
      if (results.every(Boolean) && batch.draftPostId) {
        setMessage('업로드와 사진 정제가 완료되었습니다. 비공개 초안을 여는 중입니다.');
        router.push(`/manage/drafts/${batch.draftPostId}`);
        router.refresh();
      } else if (results.every(Boolean)) {
        setMessage('업로드는 완료됐지만 초안 서버 배포가 아직 반영되지 않았습니다. 잠시 후 다시 시도해주세요.');
      }
    } catch (error) {
      setMessage(apiMessage(error));
    } finally {
      setIsPreparing(false);
    }
  }

  async function startUpload(activeBatchId: string, clientUpload: ClientUpload): Promise<boolean> {
    const ticket = clientUpload.ticket;
    if (!ticket) {
      updateUpload(clientUpload.id, {
        status: 'FAILED',
        errorMessage: '업로드 권한이 만료되었습니다. 다시 시도해주세요.',
      });
      return false;
    }

    let lastReportedPercent = 0;
    return new Promise<boolean>((resolve) => {
      const upload = new Upload(clientUpload.file, {
        endpoint: ticket.endpoint,
        chunkSize: CHUNK_SIZE,
        retryDelays: [0, 3_000, 5_000, 10_000, 20_000],
        uploadDataDuringCreation: true,
        removeFingerprintOnSuccess: true,
        headers: {
          'x-signature': ticket.token,
        },
        metadata: {
          bucketName: ticket.bucketName,
          objectName: ticket.objectName,
          contentType: clientUpload.file.type || inferMimeType(clientUpload.file.name),
          cacheControl: '3600',
        },
        onProgress(bytesUploaded, bytesTotal) {
          const progress = bytesTotal === 0 ? 0 : Math.round((bytesUploaded / bytesTotal) * 100);
          updateUpload(clientUpload.id, { progress, status: 'UPLOADING' });
          if (progress >= lastReportedPercent + 10 || bytesUploaded === bytesTotal) {
            lastReportedPercent = progress;
            void recordUploadProgress(activeBatchId, clientUpload.id, bytesUploaded).catch(() => undefined);
          }
        },
        onError(error) {
          updateUpload(clientUpload.id, {
            status: 'FAILED',
            errorMessage: error.message || '네트워크 연결을 확인해주세요.',
          });
          void reportUploadFailure(activeBatchId, clientUpload.id, 'NETWORK_ERROR')
            .catch(() => undefined);
          resolve(false);
        },
        onSuccess() {
          void completeUpload(activeBatchId, clientUpload.id)
            .then(() => sanitizeUpload(activeBatchId, clientUpload.id))
            .then((result) => {
              if (result.status === 'FAILED') {
                updateUpload(clientUpload.id, {
                  progress: 100,
                  status: 'PROCESSING_FAILED',
                  ticket: undefined,
                  errorMessage: result.message,
                });
                resolve(false);
                return;
              }
              updateUpload(clientUpload.id, {
                progress: 100,
                status: 'SANITIZED',
                ticket: undefined,
                errorMessage: undefined,
                variantCount: result.variants.length,
              });
              resolve(true);
            })
            .catch((error: unknown) => {
              updateUpload(clientUpload.id, {
                status: 'FAILED',
                errorMessage: apiMessage(error),
              });
              resolve(false);
            });
        },
      });
      uploadInstances.current.set(clientUpload.id, upload);
      void upload.findPreviousUploads().then((previousUploads) => {
        if (previousUploads.length > 0) {
          upload.resumeFromPreviousUpload(previousUploads[0]);
        }
        upload.start();
      }).catch(() => upload.start());
    });
  }

  async function handlePause(uploadId: string) {
    const upload = uploadInstances.current.get(uploadId);
    if (!upload) {
      return;
    }
    await upload.abort(false);
    updateUpload(uploadId, { status: 'PAUSED' });
  }

  function handleResume(uploadId: string) {
    const upload = uploadInstances.current.get(uploadId);
    if (!upload) {
      return;
    }
    updateUpload(uploadId, { status: 'UPLOADING' });
    upload.start();
  }

  async function handleRetry(clientUpload: ClientUpload) {
    if (!batchId) {
      return;
    }
    try {
      const item = await retryUpload(batchId, clientUpload.id);
      const nextUpload = {
        ...clientUpload,
        progress: 0,
        status: item.status,
        attemptCount: item.attemptCount,
        ticket: item.uploadTicket ?? undefined,
        errorMessage: undefined,
      } satisfies ClientUpload;
      updateUpload(clientUpload.id, nextUpload);
      const completed = await startUpload(batchId, nextUpload);
      const otherUploadsComplete = uploads.every((upload) => (
        upload.id === clientUpload.id
        || upload.status === 'PROCESSING'
        || upload.status === 'COMPLETED'
      ));
      if (completed && otherUploadsComplete && draftPostId) {
        setMessage('업로드와 사진 정제가 완료되었습니다. 비공개 초안을 여는 중입니다.');
        router.push(`/manage/drafts/${draftPostId}`);
        router.refresh();
      }
    } catch (error) {
      updateUpload(clientUpload.id, { errorMessage: apiMessage(error) });
    }
  }

  async function handleProcessingRetry(clientUpload: ClientUpload) {
    if (!batchId) {
      return;
    }
    updateUpload(clientUpload.id, { status: 'PROCESSING', errorMessage: undefined });
    try {
      const result = await sanitizeUpload(batchId, clientUpload.id);
      if (result.status === 'FAILED') {
        updateUpload(clientUpload.id, {
          status: 'PROCESSING_FAILED',
          errorMessage: result.message,
        });
        return;
      }
      updateUpload(clientUpload.id, {
        status: 'SANITIZED',
        errorMessage: undefined,
        variantCount: result.variants.length,
      });
      const otherUploadsComplete = uploads.every((upload) => (
        upload.id === clientUpload.id || upload.status === 'SANITIZED'
      ));
      if (otherUploadsComplete && draftPostId) {
        router.push(`/manage/drafts/${draftPostId}`);
        router.refresh();
      }
    } catch (error) {
      updateUpload(clientUpload.id, {
        status: 'PROCESSING_FAILED',
        errorMessage: apiMessage(error),
      });
    }
  }

  function updateUpload(uploadId: string, patch: Partial<ClientUpload>) {
    setUploads((current) => current.map((upload) => (
      upload.id === uploadId ? { ...upload, ...patch } : upload
    )));
  }

  const completedCount = uploads.filter((upload) => (
    upload.status === 'SANITIZED' || upload.status === 'COMPLETED'
  )).length;
  const allUploadsComplete = uploads.length > 0 && completedCount === uploads.length;
  const categoryLocked = isPreparing || uploads.length > 0;

  return (
    <section className="photo-uploader" aria-labelledby="photo-upload-title">
      <div className="photo-upload-heading">
        <div>
          <p className="login-status">PRIVATE UPLOAD</p>
          <h2 id="photo-upload-title">사진으로 새 기록 시작하기</h2>
          <p>JPG·PNG 사진을 최대 100장까지 선택할 수 있습니다. HEIC은 JPEG 변환 후 업로드를 권장합니다.</p>
        </div>
        {uploads.length > 0 && <strong>{completedCount} / {uploads.length}</strong>}
      </div>

      <fieldset className="photo-category-fieldset">
        <legend>어떤 기록인가요?</legend>
        <div className="photo-category-options">
          <button
            type="button"
            aria-pressed={category === 'RESTAURANT'}
            disabled={categoryLocked}
            onClick={() => setCategory('RESTAURANT')}
          >
            <strong>맛집</strong>
            <span>식당과 메뉴 기록</span>
          </button>
          <button
            type="button"
            aria-pressed={category === 'DESTINATION'}
            disabled={categoryLocked}
            onClick={() => setCategory('DESTINATION')}
          >
            <strong>여행지</strong>
            <span>장소와 여행 기록</span>
          </button>
        </div>
      </fieldset>

      <label className="photo-drop-zone">
        <input
          type="file"
          accept=".jpg,.jpeg,.png,.heic,.heif,image/jpeg,image/png,image/heic,image/heif"
          multiple
          disabled={isPreparing || !category}
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            event.target.value = '';
            void handleFiles(files);
          }}
        />
        <span>{isPreparing ? '업로드 준비 중…' : category ? '사진 선택하기' : '기록 종류를 먼저 선택해주세요'}</span>
        <small>사진당 최대 30MB · 임시 원본은 24시간 후 만료</small>
      </label>

      {message && <p className="photo-upload-message" role="alert">{message}</p>}
      {expiresAt && (
        <p className="photo-upload-expiry">
          현재 업로드 만료: {new Intl.DateTimeFormat('ko-KR', {
            dateStyle: 'medium',
            timeStyle: 'short',
          }).format(new Date(expiresAt))}
        </p>
      )}

      {allUploadsComplete && draftPostId && (
        <div className="photo-upload-complete" role="status">
          <p>모든 사진의 업로드와 개인정보 메타데이터 제거가 완료됐습니다.</p>
          <Link href={`/manage/drafts/${draftPostId}`}>초안 열기 <span>→</span></Link>
        </div>
      )}

      {uploads.length > 0 && (
        <ul className="photo-upload-list" aria-label="사진별 업로드 상태">
          {uploads.map((upload) => (
            <li key={upload.id}>
              <div className="photo-upload-file">
                <strong>{upload.file.name}</strong>
                <span>
                  {formatBytes(upload.file.size)} · {statusLabel(upload.status)}
                  {upload.variantCount ? ` · ${upload.variantCount}종` : ''}
                </span>
              </div>
              <div
                className="photo-progress"
                role="progressbar"
                aria-label={`${upload.file.name} 업로드 진행률`}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={upload.progress}
              >
                <i style={{ width: `${upload.progress}%` }} />
              </div>
              <span className="photo-progress-value">{upload.progress}%</span>
              <div className="photo-upload-actions">
                {upload.status === 'UPLOADING' && (
                  <button type="button" onClick={() => void handlePause(upload.id)}>일시정지</button>
                )}
                {upload.status === 'PAUSED' && (
                  <button type="button" onClick={() => handleResume(upload.id)}>계속</button>
                )}
                {upload.status === 'FAILED' && (
                  <button type="button" onClick={() => void handleRetry(upload)}>다시 시도</button>
                )}
                {upload.status === 'PROCESSING_FAILED' && (
                  <button type="button" onClick={() => void handleProcessingRetry(upload)}>정제 다시 시도</button>
                )}
              </div>
              {upload.errorMessage && <p role="alert">{upload.errorMessage}</p>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function validateFiles(files: File[]): string | undefined {
  if (files.length === 0) {
    return '업로드할 사진을 선택해주세요.';
  }
  if (files.length > MAX_FILE_COUNT) {
    return '한 번에 최대 100장까지 선택할 수 있습니다.';
  }
  const unsupported = files.find((file) => !ACCEPTED_MIME_TYPES.has(
    file.type || inferMimeType(file.name),
  ));
  if (unsupported) {
    return `${unsupported.name}: JPG, HEIC 또는 PNG 파일만 업로드할 수 있습니다.`;
  }
  const oversized = files.find((file) => file.size > MAX_FILE_SIZE);
  if (oversized) {
    return `${oversized.name}: 사진 한 장은 30MB 이하여야 합니다.`;
  }
  return undefined;
}

function inferMimeType(fileName: string): string {
  const extension = fileName.split('.').pop()?.toLowerCase();
  if (extension === 'jpg' || extension === 'jpeg') return 'image/jpeg';
  if (extension === 'png') return 'image/png';
  if (extension === 'heic') return 'image/heic';
  if (extension === 'heif') return 'image/heif';
  return 'application/octet-stream';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

function statusLabel(status: ClientUploadStatus): string {
  const labels: Record<ClientUploadStatus, string> = {
    PENDING: '대기 중',
    UPLOADING: '업로드 중',
    PAUSED: '일시정지',
    PROCESSING: '업로드 완료 · 처리 대기',
    SANITIZED: '정제 · 반응형 이미지 준비 완료',
    PROCESSING_FAILED: '사진 정제 실패',
    COMPLETED: '처리 완료',
    FAILED: '업로드 실패',
    EXPIRED: '만료됨',
  };
  return labels[status];
}

function apiMessage(error: unknown): string {
  if (error instanceof PhotoUploadApiError) return error.message;
  if (error instanceof Error) return error.message;
  return '사진 업로드 중 문제가 발생했습니다.';
}

async function runWithConcurrency<T, R>(
  items: T[],
  concurrency: number,
  worker: (item: T) => Promise<R>,
): Promise<R[]> {
  let nextIndex = 0;
  const results = new Array<R>(items.length);
  const runners = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      const item = items[index];
      nextIndex += 1;
      results[index] = await worker(item);
    }
  });
  await Promise.all(runners);
  return results;
}
