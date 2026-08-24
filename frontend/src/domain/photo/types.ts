export type UploadItemStatus =
  | 'PENDING'
  | 'UPLOADING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'EXPIRED';

export type PostCategory = 'RESTAURANT' | 'DESTINATION';

export interface UploadTicket {
  endpoint: string;
  token: string;
  bucketName: string;
  objectName: string;
}

export interface UploadItem {
  id: string;
  clientFileName: string;
  mimeType: string;
  byteSize: number;
  uploadedBytes: number;
  status: UploadItemStatus;
  attemptCount: number;
  failureCode: string | null;
  expiresAt: string;
  uploadTicket: UploadTicket | null;
}

export interface UploadBatch {
  id: string;
  draftPostId?: string;
  status: string;
  expiresAt: string;
  items: UploadItem[];
}

export interface UploadFileDescriptor {
  clientFileName: string;
  mimeType: string;
  byteSize: number;
}
