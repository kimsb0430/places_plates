package com.placesplates.domain.photo.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.dto.ImageSanitizationResponse;
import com.placesplates.domain.photo.entity.ImageProcessingJob;
import com.placesplates.domain.photo.entity.ImageProcessingJobStatus;
import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.exception.ImageProcessingJobException;
import com.placesplates.domain.photo.repository.ImageProcessingJobRepository;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadItemRepository;
import com.placesplates.infra.image.ImageSanitizationException;
import com.placesplates.infra.image.ImageSanitizer;
import com.placesplates.infra.image.SanitizedImage;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.StorageAccessException;

@Service
public class ImageSanitizationService {

	private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

	private final ImageProcessingJobRepository jobRepository;
	private final UploadItemRepository uploadItemRepository;
	private final PhotoRepository photoRepository;
	private final PhotoAssetRepository photoAssetRepository;
	private final ImageSanitizer imageSanitizer;
	private final PrivatePhotoStorage photoStorage;

	public ImageSanitizationService(
		ImageProcessingJobRepository jobRepository,
		UploadItemRepository uploadItemRepository,
		PhotoRepository photoRepository,
		PhotoAssetRepository photoAssetRepository,
		ImageSanitizer imageSanitizer,
		PrivatePhotoStorage photoStorage
	) {
		this.jobRepository = jobRepository;
		this.uploadItemRepository = uploadItemRepository;
		this.photoRepository = photoRepository;
		this.photoAssetRepository = photoAssetRepository;
		this.imageSanitizer = imageSanitizer;
		this.photoStorage = photoStorage;
	}

	@Transactional
	public ImageSanitizationResponse sanitize(UUID ownerUserId, UUID uploadBatchId, UUID uploadItemId) {
		ImageProcessingJob job = jobRepository.findLockedByUploadItemIdAndOwnerUserId(uploadItemId, ownerUserId)
			.orElseThrow(() -> notFound("이미지 처리 작업을 찾을 수 없습니다."));
		UploadItem uploadItem = uploadItemRepository.findByIdAndUploadBatchIdAndUploadBatchOwnerUserId(
			uploadItemId,
			uploadBatchId,
			ownerUserId
		)
			.orElseThrow(() -> notFound("사진 업로드 항목을 찾을 수 없습니다."));

		if (job.getStatus() == ImageProcessingJobStatus.COMPLETED && uploadItem.getResultPhotoId() != null) {
			return ImageSanitizationResponse.completed(job.getId(), uploadItemId, uploadItem.getResultPhotoId());
		}

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		if (job.getStatus() == ImageProcessingJobStatus.FAILED && job.canRetry()) {
			job.retryNow(now);
		}
		try {
			job.start(now);
		} catch (IllegalStateException exception) {
			return ImageSanitizationResponse.failed(
				job.getId(),
				uploadItemId,
				"IMAGE_PROCESSING_JOB_NOT_READY",
				"이미지 처리 작업을 아직 다시 시작할 수 없습니다. 잠시 후 시도해주세요."
			);
		}

		try {
			byte[] source = photoStorage.downloadTemporary(uploadItem.getTemporaryStorageKey());
			if (source.length != uploadItem.getByteSize()) {
				throw new ImageSanitizationException(
					"TEMPORARY_OBJECT_SIZE_MISMATCH",
					"업로드된 사진의 크기가 기록과 일치하지 않습니다."
				);
			}
			SanitizedImage sanitized = imageSanitizer.sanitize(source, uploadItem.getMimeType());
			String storageKey = sanitizedStorageKey(ownerUserId, job.getId());
			photoStorage.storeSanitizedMaster(storageKey, sanitized.bytes(), sanitized.mimeType());

			Photo photo = photoRepository.save(Photo.processing(ownerUserId, job.getPostId()));
			photoAssetRepository.save(PhotoAsset.sanitizedMaster(
				photo.getId(),
				storageKey,
				sanitized.mimeType(),
				sanitized.width(),
				sanitized.height(),
				sanitized.bytes().length
			));
			uploadItem.assignResultPhoto(photo.getId());
			job.complete(OffsetDateTime.now(ZoneOffset.UTC));
			return ImageSanitizationResponse.completed(job.getId(), uploadItemId, photo.getId());
		} catch (ImageSanitizationException exception) {
			return fail(job, uploadItemId, exception.getFailureCode(), exception.getMessage());
		} catch (StorageAccessException exception) {
			return fail(
				job,
				uploadItemId,
				"PHOTO_STORAGE_UNAVAILABLE",
				"사진 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해주세요."
			);
		}
	}

	private ImageSanitizationResponse fail(
		ImageProcessingJob job,
		UUID uploadItemId,
		String failureCode,
		String message
	) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		job.fail(failureCode, now.plus(RETRY_DELAY), now);
		return ImageSanitizationResponse.failed(job.getId(), uploadItemId, failureCode, message);
	}

	private static String sanitizedStorageKey(UUID ownerUserId, UUID jobId) {
		return "sanitized/" + ownerUserId + "/" + jobId + ".jpg";
	}

	private static ImageProcessingJobException notFound(String message) {
		return new ImageProcessingJobException(
			HttpStatus.NOT_FOUND,
			"IMAGE_PROCESSING_JOB_NOT_FOUND",
			message
		);
	}
}
