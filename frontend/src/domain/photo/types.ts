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

export interface ImageSanitizationResult {
  jobId: string;
  uploadItemId: string;
  photoId: string | null;
  status: 'COMPLETED' | 'FAILED';
  failureCode: string | null;
  message: string;
  variants: ImageVariant[];
}

export type ImageVariantType = 'THUMBNAIL' | 'MAP_CARD' | 'PUBLIC_DETAIL';

export interface ImageVariant {
  type: ImageVariantType;
  width: number;
  height: number;
  byteSize: number;
}

export interface UploadFileDescriptor {
  clientFileName: string;
  mimeType: string;
  byteSize: number;
}

export type PhotoProcessingStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface DraftPhoto {
  id: string;
  displayOrder: number;
  cover: boolean;
  altText: string | null;
  processingStatus: PhotoProcessingStatus;
  thumbnailPath: string | null;
}

export interface DraftPhotoEditItem {
  photoId: string;
  cover: boolean;
  altText: string | null;
}
