package com.placesplates.domain.photo.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadItemRepository;
import com.placesplates.global.security.DatabaseOwnerScope;
import com.placesplates.infra.image.ImageSanitizationException;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.StorageAccessException;

@Service
public class TemporaryOriginalCleanupService {

	private final DatabaseOwnerScope databaseOwnerScope;
	private final UploadItemRepository uploadItemRepository;
	private final PhotoRepository photoRepository;
	private final PhotoAssetVerificationService verificationService;
	private final PrivatePhotoStorage photoStorage;

	public TemporaryOriginalCleanupService(
		DatabaseOwnerScope databaseOwnerScope,
		UploadItemRepository uploadItemRepository,
		PhotoRepository photoRepository,
		PhotoAssetVerificationService verificationService,
		PrivatePhotoStorage photoStorage
	) {
		this.databaseOwnerScope = databaseOwnerScope;
		this.uploadItemRepository = uploadItemRepository;
		this.photoRepository = photoRepository;
		this.verificationService = verificationService;
		this.photoStorage = photoStorage;
	}

	@Transactional
	public int purgeOwner(UUID ownerUserId, int batchSize) {
		databaseOwnerScope.activateOwner(ownerUserId);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		int processed = purgeProcessed(ownerUserId, now, batchSize);
		int remaining = Math.max(0, batchSize - processed);
		return processed + purgeExpired(ownerUserId, now, remaining);
	}

	private int purgeProcessed(UUID ownerUserId, OffsetDateTime now, int limit) {
		if (limit == 0) {
			return 0;
		}
		List<UploadItem> items = uploadItemRepository.findProcessedOriginalsPendingDeletion(
			ownerUserId,
			PageRequest.of(0, limit)
		);
		int purged = 0;
		for (UploadItem item : items) {
			Photo photo = photoRepository.findById(item.getResultPhotoId()).orElse(null);
			if (photo == null) {
				continue;
			}
			photo.markProcessing();
			try {
				verificationService.verifyAndPublish(photo.getId());
				photoStorage.deleteTemporary(item.getTemporaryStorageKey());
				item.completeAndForgetOriginal(now);
				photo.markReady();
				purged++;
			} catch (ImageSanitizationException exception) {
				if (deleteRejectedOriginal(item, exception.getFailureCode(), now)) {
					photo.markFailed();
					purged++;
				}
			} catch (StorageAccessException exception) {
				photo.markProcessing();
			}
		}
		return purged;
	}

	private boolean deleteRejectedOriginal(UploadItem item, String failureCode, OffsetDateTime now) {
		try {
			photoStorage.deleteTemporary(item.getTemporaryStorageKey());
			item.failAndForgetOriginal(failureCode, now);
			return true;
		} catch (StorageAccessException exception) {
			return false;
		}
	}

	private int purgeExpired(UUID ownerUserId, OffsetDateTime now, int limit) {
		if (limit == 0) {
			return 0;
		}
		List<UploadItem> items = uploadItemRepository.findExpiredOriginalsPendingDeletion(
			ownerUserId,
			now,
			PageRequest.of(0, limit)
		);
		int purged = 0;
		for (UploadItem item : items) {
			try {
				photoStorage.deleteTemporary(item.getTemporaryStorageKey());
				item.expireAndForgetOriginal(now);
				purged++;
			} catch (StorageAccessException exception) {
				// 保存先の一時障害ではDB上の参照を残し、次回の定期処理で再試行する。
			}
		}
		return purged;
	}
}
