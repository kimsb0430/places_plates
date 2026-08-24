'use client';

import { useRef, useState } from 'react';
import { Upload } from 'tus-js-client';
import {
  completeUpload,
  createUploadBatch,
  PhotoUploadApiError,
  recordUploadProgress,
  reportUploadFailure,
  retryUpload,
} from '../api/photo-upload-api';
import type { UploadItem, UploadTicket } from '../types';

const MAX_FILE_COUNT = 100;
const MAX_FILE_SIZE = 30 * 1024 * 1024;
const CHUNK_SIZE = 6 * 1024 * 1024;
const ACCEPTED_MIME_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/heic',
  'image/heif',
]);

type ClientUploadStatus = UploadItem['status'] | 'PAUSED';

interface ClientUpload {
  id: string;
  file: File;
  progress: number;
  status: ClientUploadStatus;
  attemptCount: number;
  errorMessage?: string;
  ticket?: UploadTicket;
}

export function PhotoUploader() {
  const [batchId, setBatchId] = useState<string>();
  const [expiresAt, setExpiresAt] = useState<string>();
  const [uploads, setUploads] = useState<ClientUpload[]>([]);
  const [isPreparing, setIsPreparing] = useState(false);
  const [message, setMessage] = useState<string>();
  const uploadInstances = useRef(new Map<string, Upload>());

  async function handleFiles(files: File[]) {
    setMessage(undefined);
    const validationMessage = validateFiles(files);
    if (validationMessage) {
      setMessage(validationMessage);
      return;
    }

    setIsPreparing(true);
    try {
      const batch = await createUploadBatch(files.map((file) => ({
        clientFileName: file.name,
        mimeType: file.type || inferMimeType(file.name),
        byteSize: file.size,
      })));
      setBatchId(batch.id);
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
      await runWithConcurrency(nextUploads, 3, (upload) => startUpload(batch.id, upload));
    } catch (error) {
      setMessage(apiMessage(error));
    } finally {
      setIsPreparing(false);
    }
  }

  async function startUpload(activeBatchId: string, clientUpload: ClientUpload): Promise<void> {
    const ticket = clientUpload.ticket;
    if (!ticket) {
      updateUpload(clientUpload.id, {
        status: 'FAILED',
        errorMessage: '업로드 권한이 만료되었습니다. 다시 시도해주세요.',
      });
      return;
    }

    let lastReportedPercent = 0;
    await new Promise<void>((resolve) => {
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
          resolve();
        },
        onSuccess() {
          void completeUpload(activeBatchId, clientUpload.id)
            .then((item) => {
              updateUpload(clientUpload.id, {
                progress: 100,
                status: item.status,
                ticket: undefined,
                errorMessage: undefined,
              });
            })
            .catch((error: unknown) => {
              updateUpload(clientUpload.id, {
                status: 'FAILED',
                errorMessage: apiMessage(error),
              });
            })
            .finally(resolve);
        },
      });
      uploadInstances.current.set(clientUpload.id, upload);
      void upload.findPreviousUploads().then((previousUploads) => {
        if (previousUploads.length > 0) {
          upload.resumeFromPreviousUpload(previousUploads[0]);
        }
        upload.start();
      });
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
      await startUpload(batchId, nextUpload);
    } catch (error) {
      updateUpload(clientUpload.id, { errorMessage: apiMessage(error) });
    }
  }

  function updateUpload(uploadId: string, patch: Partial<ClientUpload>) {
    setUploads((current) => current.map((upload) => (
      upload.id === uploadId ? { ...upload, ...patch } : upload
    )));
  }

  const completedCount = uploads.filter((upload) => (
    upload.status === 'PROCESSING' || upload.status === 'COMPLETED'
  )).length;

  return (
    <section className="photo-uploader" aria-labelledby="photo-upload-title">
      <div className="photo-upload-heading">
        <div>
          <p className="login-status">PRIVATE UPLOAD</p>
          <h2 id="photo-upload-title">사진으로 새 기록 시작하기</h2>
          <p>JPG·HEIC·PNG 사진을 최대 100장까지 선택할 수 있습니다.</p>
        </div>
        {uploads.length > 0 && <strong>{completedCount} / {uploads.length}</strong>}
      </div>

      <label className="photo-drop-zone">
        <input
          type="file"
          accept=".jpg,.jpeg,.png,.heic,.heif,image/jpeg,image/png,image/heic,image/heif"
          multiple
          disabled={isPreparing}
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            event.target.value = '';
            void handleFiles(files);
          }}
        />
        <span>{isPreparing ? '업로드 준비 중…' : '사진 선택하기'}</span>
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

      {uploads.length > 0 && (
        <ul className="photo-upload-list" aria-label="사진별 업로드 상태">
          {uploads.map((upload) => (
            <li key={upload.id}>
              <div className="photo-upload-file">
                <strong>{upload.file.name}</strong>
                <span>{formatBytes(upload.file.size)} · {statusLabel(upload.status)}</span>
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

async function runWithConcurrency<T>(
  items: T[],
  concurrency: number,
  worker: (item: T) => Promise<void>,
): Promise<void> {
  let nextIndex = 0;
  const runners = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (nextIndex < items.length) {
      const item = items[nextIndex];
      nextIndex += 1;
      await worker(item);
    }
  });
  await Promise.all(runners);
}
