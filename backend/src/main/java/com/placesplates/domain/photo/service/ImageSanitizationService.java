package com.placesplates.domain.photo.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.dto.ImageSanitizationResponse;
import com.placesplates.domain.photo.entity.ImageProcessingJob;
import com.placesplates.domain.photo.entity.ImageProcessingJobStatus;
import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.entity.UploadItemStatus;
import com.placesplates.domain.photo.exception.ImageProcessingJobException;
import com.placesplates.domain.photo.repository.ImageProcessingJobRepository;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadItemRepository;
import com.placesplates.infra.image.ImageSanitizationException;
import com.placesplates.infra.image.ImageSanitizer;
import com.placesplates.infra.image.ResponsiveImageGenerator;
import com.placesplates.infra.image.ResponsiveImageVariant;
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
	private final ResponsiveImageGenerator responsiveImageGenerator;
	private final PhotoAssetVerificationService verificationService;
	private final PrivatePhotoStorage photoStorage;

	public ImageSanitizationService(
		ImageProcessingJobRepository jobRepository,
		UploadItemRepository uploadItemRepository,
		PhotoRepository photoRepository,
		PhotoAssetRepository photoAssetRepository,
		ImageSanitizer imageSanitizer,
		ResponsiveImageGenerator responsiveImageGenerator,
		PhotoAssetVerificationService verificationService,
		PrivatePhotoStorage photoStorage
	) {
		this.jobRepository = jobRepository;
		this.uploadItemRepository = uploadItemRepository;
		this.photoRepository = photoRepository;
		this.photoAssetRepository = photoAssetRepository;
		this.imageSanitizer = imageSanitizer;
		this.responsiveImageGenerator = responsiveImageGenerator;
		this.verificationService = verificationService;
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

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		if (uploadItem.getResultPhotoId() != null) {
			return finalizeExistingPhoto(ownerUserId, job, uploadItem, now);
		}
		if (!startJob(job, now)) {
			return notReady(job, uploadItemId);
		}

		Photo photo = null;
		try {
			byte[] source = photoStorage.downloadTemporary(uploadItem.getTemporaryStorageKey());
			if (source.length != uploadItem.getByteSize()) {
				throw new ImageSanitizationException(
					"TEMPORARY_OBJECT_SIZE_MISMATCH",
					"업로드된 사진의 크기가 기록과 일치하지 않습니다."
				);
			}
			SanitizedImage sanitized = imageSanitizer.sanitize(source, uploadItem.getMimeType());
			List<ResponsiveImageVariant> variants = responsiveImageGenerator.generate(sanitized);
			String storageKey = sanitizedStorageKey(ownerUserId, job.getId());
			photoStorage.storeSanitizedMaster(storageKey, sanitized.bytes(), sanitized.mimeType());

			photo = photoRepository.save(Photo.processing(ownerUserId, job.getPostId()));
			photoAssetRepository.save(PhotoAsset.sanitizedMaster(
				photo.getId(), storageKey, sanitized.mimeType(), sanitized.width(), sanitized.height(), sanitized.bytes().length
			));
			for (ResponsiveImageVariant variant : variants) {
				storePrivateVariant(ownerUserId, job.getId(), photo.getId(), variant);
			}
			uploadItem.assignResultPhoto(photo.getId());
			List<PhotoAsset> assets = verificationService.verifyAndPublish(photo.getId());
			photoStorage.deleteTemporary(uploadItem.getTemporaryStorageKey());
			uploadItem.completeAndForgetOriginal(OffsetDateTime.now(ZoneOffset.UTC));
			photo.markReady();
			job.complete(OffsetDateTime.now(ZoneOffset.UTC));
			return ImageSanitizationResponse.completed(job.getId(), uploadItemId, photo.getId(), assets);
		} catch (ImageSanitizationException exception) {
			if (photo != null) {
				photo.markFailed();
				deleteRejectedOriginal(uploadItem, exception.getFailureCode());
			}
			return fail(job, uploadItemId, exception.getFailureCode(), exception.getMessage());
		} catch (StorageAccessException exception) {
			return fail(job, uploadItemId, "PHOTO_STORAGE_UNAVAILABLE",
				"사진 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
		}
	}

	private ImageSanitizationResponse finalizeExistingPhoto(
		UUID ownerUserId, ImageProcessingJob job, UploadItem uploadItem, OffsetDateTime now
	) {
		if (job.getStatus() != ImageProcessingJobStatus.COMPLETED && !startJob(job, now)) {
			return notReady(job, uploadItem.getId());
		}
		Photo photo = photoRepository.findById(uploadItem.getResultPhotoId())
			.orElseThrow(() -> notFound("처리된 사진을 찾을 수 없습니다."));
		boolean forceRegeneration = photo.getProcessingStatus() == PhotoProcessingStatus.FAILED;
		photo.markProcessing();
		try {
			ensureResponsiveVariants(ownerUserId, job.getId(), photo.getId(), forceRegeneration);
			List<PhotoAsset> assets = verificationService.verifyAndPublish(photo.getId());
			if (uploadItem.getTemporaryStorageKey() != null) {
				photoStorage.deleteTemporary(uploadItem.getTemporaryStorageKey());
				uploadItem.completeAndForgetOriginal(OffsetDateTime.now(ZoneOffset.UTC));
			} else if (uploadItem.getOriginalDeletedAt() != null
				&& uploadItem.getStatus() != UploadItemStatus.COMPLETED) {
				uploadItem.completeAndForgetOriginal(uploadItem.getOriginalDeletedAt());
			} else if (uploadItem.getStatus() != UploadItemStatus.COMPLETED) {
				throw new ImageSanitizationException(
					"TEMPORARY_ORIGINAL_STATE_INVALID",
					"임시 원본의 삭제 상태를 확인할 수 없습니다."
				);
			}
			photo.markReady();
			if (job.getStatus() == ImageProcessingJobStatus.PROCESSING) {
				job.complete(OffsetDateTime.now(ZoneOffset.UTC));
			}
			return ImageSanitizationResponse.completed(job.getId(), uploadItem.getId(), photo.getId(), assets);
		} catch (ImageSanitizationException exception) {
			photo.markFailed();
			deleteRejectedOriginal(uploadItem, exception.getFailureCode());
			return failIfProcessing(job, uploadItem.getId(), exception.getFailureCode(), exception.getMessage());
		} catch (StorageAccessException exception) {
			return failIfProcessing(job, uploadItem.getId(), "PHOTO_STORAGE_UNAVAILABLE",
				"사진 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
		}
	}

	private boolean startJob(ImageProcessingJob job, OffsetDateTime now) {
		if (job.getStatus() == ImageProcessingJobStatus.FAILED && job.canRetry()) {
			job.retryNow(now);
		}
		try {
			job.start(now);
			return true;
		} catch (IllegalStateException exception) {
			return false;
		}
	}

	private void deleteRejectedOriginal(UploadItem uploadItem, String failureCode) {
		if (uploadItem.getTemporaryStorageKey() == null) {
			return;
		}
		try {
			photoStorage.deleteTemporary(uploadItem.getTemporaryStorageKey());
			uploadItem.failAndForgetOriginal(failureCode, OffsetDateTime.now(ZoneOffset.UTC));
		} catch (StorageAccessException exception) {
			// 削除失敗時は参照を保持し、定期cleanupで同じobjectを再削除する。
		}
	}

	private void storePrivateVariant(UUID ownerUserId, UUID jobId, UUID photoId, ResponsiveImageVariant variant) {
		String variantStorageKey = responsiveStorageKey(ownerUserId, jobId, variant.type());
		photoStorage.storeResponsiveVariant(variantStorageKey, variant.bytes(), variant.mimeType());
		photoAssetRepository.save(PhotoAsset.privateResponsiveVariant(
			photoId, variant.type(), variantStorageKey, variant.mimeType(),
			variant.width(), variant.height(), variant.bytes().length
		));
	}

	private ImageSanitizationResponse fail(
		ImageProcessingJob job, UUID uploadItemId, String failureCode, String message
	) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		job.fail(failureCode, now.plus(RETRY_DELAY), now);
		return ImageSanitizationResponse.failed(job.getId(), uploadItemId, failureCode, message);
	}

	private ImageSanitizationResponse failIfProcessing(
		ImageProcessingJob job, UUID uploadItemId, String failureCode, String message
	) {
		if (job.getStatus() == ImageProcessingJobStatus.PROCESSING) {
			return fail(job, uploadItemId, failureCode, message);
		}
		return ImageSanitizationResponse.failed(job.getId(), uploadItemId, failureCode, message);
	}

	private static ImageSanitizationResponse notReady(ImageProcessingJob job, UUID uploadItemId) {
		return ImageSanitizationResponse.failed(
			job.getId(), uploadItemId, "IMAGE_PROCESSING_JOB_NOT_READY",
			"이미지 처리 작업을 아직 다시 시작할 수 없습니다. 잠시 후 시도해주세요."
		);
	}

	private static String sanitizedStorageKey(UUID ownerUserId, UUID jobId) {
		return "sanitized/" + ownerUserId + "/" + jobId + ".jpg";
	}

	private List<PhotoAsset> ensureResponsiveVariants(
		UUID ownerUserId,
		UUID jobId,
		UUID photoId,
		boolean forceRegeneration
	) {
		List<PhotoAsset> assets = photoAssetRepository.findAllByPhotoId(photoId);
		Map<PhotoAssetVariantType, PhotoAsset> assetsByType = new EnumMap<>(PhotoAssetVariantType.class);
		assets.forEach(asset -> assetsByType.put(asset.getVariantType(), asset));
		if (!forceRegeneration && responsiveVariantTypes().stream().allMatch(type -> {
			PhotoAsset asset = assetsByType.get(type);
			return asset != null && asset.usesWatermarkPolicy(
				responsiveImageGenerator.watermarkVersion(), responsiveImageGenerator.watermarkPosition()
			);
		})) {
			return assets;
		}

		PhotoAsset masterAsset = assets.stream()
			.filter(asset -> asset.getVariantType() == PhotoAssetVariantType.SANITIZED_MASTER)
			.findFirst()
			.orElseThrow(() -> new ImageSanitizationException(
				"SANITIZED_MASTER_NOT_FOUND", "반응형 이미지의 기준이 되는 정제 마스터를 찾을 수 없습니다."
			));
		byte[] masterBytes = photoStorage.downloadSanitizedMaster(masterAsset.getStorageKey());
		SanitizedImage master = new SanitizedImage(
			masterBytes, masterAsset.getMimeType(), masterAsset.getWidth(), masterAsset.getHeight()
		);
		for (ResponsiveImageVariant variant : responsiveImageGenerator.generate(master)) {
			PhotoAsset existingAsset = assetsByType.get(variant.type());
			if (!forceRegeneration && existingAsset != null && existingAsset.usesWatermarkPolicy(
				variant.watermarkVersion(), variant.watermarkPosition()
			)) {
				continue;
			}
			String variantStorageKey = responsiveStorageKey(ownerUserId, jobId, variant.type());
			photoStorage.storeResponsiveVariant(variantStorageKey, variant.bytes(), variant.mimeType());
			if (existingAsset == null) {
				photoAssetRepository.save(PhotoAsset.privateResponsiveVariant(
					photoId, variant.type(), variantStorageKey, variant.mimeType(),
					variant.width(), variant.height(), variant.bytes().length
				));
			} else {
				existingAsset.stagePrivateVariant(
					variantStorageKey, variant.mimeType(), variant.width(), variant.height(), variant.bytes().length
				);
			}
		}
		return photoAssetRepository.findAllByPhotoId(photoId);
	}

	private static Set<PhotoAssetVariantType> responsiveVariantTypes() {
		return EnumSet.of(
			PhotoAssetVariantType.THUMBNAIL,
			PhotoAssetVariantType.MAP_CARD,
			PhotoAssetVariantType.PUBLIC_DETAIL
		);
	}

	private static String responsiveStorageKey(
		UUID ownerUserId, UUID jobId, PhotoAssetVariantType variantType
	) {
		return "variants/" + ownerUserId + "/" + jobId + "/"
			+ variantType.name().toLowerCase(java.util.Locale.ROOT) + ".jpg";
	}

	private static ImageProcessingJobException notFound(String message) {
		return new ImageProcessingJobException(
			HttpStatus.NOT_FOUND, "IMAGE_PROCESSING_JOB_NOT_FOUND", message
		);
	}
}
