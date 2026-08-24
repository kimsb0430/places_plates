package com.placesplates.domain.photo.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.placesplates.domain.photo.dto.CreateUploadBatchRequest;
import com.placesplates.domain.photo.dto.UploadBatchResponse;
import com.placesplates.domain.photo.dto.UploadFileRequest;
import com.placesplates.domain.photo.dto.UploadItemResponse;
import com.placesplates.domain.photo.dto.UploadTicketResponse;
import com.placesplates.domain.photo.entity.UploadBatch;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.entity.UploadItemStatus;
import com.placesplates.domain.photo.exception.PhotoUploadException;
import com.placesplates.domain.photo.repository.UploadBatchRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.infra.storage.SignedUploadTicket;
import com.placesplates.infra.storage.StorageAccessException;
import com.placesplates.infra.storage.TemporaryUploadSigner;

@Service
@Transactional(readOnly = true)
public class PhotoUploadService {

	private static final Duration UPLOAD_EXPIRY = Duration.ofHours(24);
	private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
		"image/jpeg",
		"image/png",
		"image/heic",
		"image/heif"
	);
	private static final Map<String, String> SAFE_EXTENSIONS = Map.of(
		"image/jpeg", ".jpg",
		"image/png", ".png",
		"image/heic", ".heic",
		"image/heif", ".heif"
	);

	private final UploadBatchRepository uploadBatchRepository;
	private final DraftPostRepository draftPostRepository;
	private final TemporaryUploadSigner uploadSigner;

	public PhotoUploadService(
		UploadBatchRepository uploadBatchRepository,
		DraftPostRepository draftPostRepository,
		TemporaryUploadSigner uploadSigner
	) {
		this.uploadBatchRepository = uploadBatchRepository;
		this.draftPostRepository = draftPostRepository;
		this.uploadSigner = uploadSigner;
	}

	@Transactional
	public UploadBatchResponse createBatch(UUID ownerUserId, CreateUploadBatchRequest request) {
		OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(UPLOAD_EXPIRY);
		PostCategory category = request.category() == null
			? PostCategory.DESTINATION
			: request.category();
		DraftPost draft = draftPostRepository.save(DraftPost.create(ownerUserId, category));
		UploadBatch batch = UploadBatch.create(ownerUserId, expiresAt);
		batch.assignPost(draft.getId());
		for (UploadFileRequest file : request.files()) {
			String mimeType = normalizeMimeType(file.mimeType());
			batch.addItem(UploadItem.create(
				sanitizeClientFileName(file.clientFileName()),
				mimeType,
				file.byteSize(),
				expiresAt
			));
		}
		List<UploadItemResponse> items = new ArrayList<>();
		for (UploadItem item : batch.getItems()) {
			String storageKey = storageKey(ownerUserId, batch.getId(), item);
			item.start(storageKey);
			items.add(UploadItemResponse.from(item, issueTicket(storageKey)));
		}
		uploadBatchRepository.save(batch);
		return UploadBatchResponse.from(batch, items);
	}

	public UploadBatchResponse getBatch(UUID ownerUserId, UUID batchId) {
		UploadBatch batch = getOwnedBatch(ownerUserId, batchId);
		return UploadBatchResponse.from(
			batch,
			batch.getItems().stream().map(item -> UploadItemResponse.from(item, null)).toList()
		);
	}

	@Transactional
	public UploadItemResponse recordProgress(
		UUID ownerUserId,
		UUID batchId,
		UUID itemId,
		long uploadedBytes
	) {
		UploadBatch batch = getOwnedBatch(ownerUserId, batchId);
		UploadItem item = getItem(batch, itemId);
		ensureActive(item);
		ensureUploading(item);
		try {
			item.recordProgress(uploadedBytes);
		} catch (IllegalArgumentException exception) {
			throw invalidState("PHOTO_UPLOAD_INVALID_PROGRESS", "업로드 진행률을 확인해주세요.");
		}
		batch.refreshStatus();
		return UploadItemResponse.from(item, null);
	}

	@Transactional
	public UploadItemResponse markFailed(
		UUID ownerUserId,
		UUID batchId,
		UUID itemId,
		String failureCode
	) {
		UploadBatch batch = getOwnedBatch(ownerUserId, batchId);
		UploadItem item = getItem(batch, itemId);
		ensureActive(item);
		ensureUploading(item);
		item.markFailed(failureCode);
		batch.refreshStatus();
		return UploadItemResponse.from(item, null);
	}

	@Transactional
	public UploadItemResponse retry(UUID ownerUserId, UUID batchId, UUID itemId) {
		UploadBatch batch = getOwnedBatch(ownerUserId, batchId);
		UploadItem item = getItem(batch, itemId);
		ensureActive(item);
		if (item.getStatus() != UploadItemStatus.FAILED) {
			throw invalidState("PHOTO_UPLOAD_NOT_RETRYABLE", "실패한 사진만 다시 시도할 수 있습니다.");
		}
		try {
			item.retry();
		} catch (IllegalStateException exception) {
			throw invalidState("PHOTO_UPLOAD_RETRY_LIMIT", "사진 업로드 재시도 한도를 초과했습니다.");
		}
		String storageKey = ensureStorageKey(ownerUserId, batchId, item);
		batch.refreshStatus();
		return UploadItemResponse.from(item, issueTicket(storageKey));
	}

	@Transactional
	public UploadItemResponse complete(UUID ownerUserId, UUID batchId, UUID itemId) {
		UploadBatch batch = getOwnedBatch(ownerUserId, batchId);
		UploadItem item = getItem(batch, itemId);
		ensureActive(item);
		ensureUploading(item);
		String storageKey = ensureStorageKey(ownerUserId, batchId, item);
		boolean isMatchingObject;
		try {
			isMatchingObject = uploadSigner.objectMatches(storageKey, item.getByteSize());
		} catch (StorageAccessException exception) {
			throw new PhotoUploadException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"PHOTO_STORAGE_UNAVAILABLE",
				"업로드된 사진을 확인하지 못했습니다. 잠시 후 다시 시도해주세요."
			);
		}
		if (!isMatchingObject) {
			throw invalidState("PHOTO_UPLOAD_OBJECT_MISMATCH", "업로드된 사진의 크기를 확인할 수 없습니다.");
		}
		item.markUploaded();
		batch.refreshStatus();
		return UploadItemResponse.from(item, null);
	}

	private UploadBatch getOwnedBatch(UUID ownerUserId, UUID batchId) {
		return uploadBatchRepository.findWithItemsByIdAndOwnerUserId(batchId, ownerUserId)
			.orElseThrow(() -> new PhotoUploadException(
				HttpStatus.NOT_FOUND,
				"PHOTO_UPLOAD_BATCH_NOT_FOUND",
				"사진 업로드 묶음을 찾을 수 없습니다."
			));
	}

	private UploadItem getItem(UploadBatch batch, UUID itemId) {
		return batch.getItems().stream()
			.filter(item -> item.getId().equals(itemId))
			.findFirst()
			.orElseThrow(() -> new PhotoUploadException(
				HttpStatus.NOT_FOUND,
				"PHOTO_UPLOAD_ITEM_NOT_FOUND",
				"사진 업로드 항목을 찾을 수 없습니다."
			));
	}

	private UploadTicketResponse issueTicket(String storageKey) {
		try {
			SignedUploadTicket ticket = uploadSigner.issue(storageKey);
			return new UploadTicketResponse(
				ticket.endpoint(),
				ticket.token(),
				ticket.bucketName(),
				ticket.objectName()
			);
		} catch (StorageAccessException exception) {
			throw new PhotoUploadException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"PHOTO_STORAGE_UNAVAILABLE",
				"사진 저장소를 준비하지 못했습니다. 잠시 후 다시 시도해주세요."
			);
		}
	}

	private static void ensureActive(UploadItem item) {
		if (item.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			throw new PhotoUploadException(
				HttpStatus.GONE,
				"PHOTO_UPLOAD_EXPIRED",
				"사진 업로드 시간이 만료되었습니다."
			);
		}
	}

	private static void ensureUploading(UploadItem item) {
		if (item.getStatus() != UploadItemStatus.UPLOADING
			&& item.getStatus() != UploadItemStatus.PENDING) {
			throw invalidState("PHOTO_UPLOAD_INVALID_STATE", "현재 상태에서는 업로드를 변경할 수 없습니다.");
		}
	}

	private static String normalizeMimeType(String mimeType) {
		String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_MIME_TYPES.contains(normalized)) {
			throw new PhotoUploadException(
				HttpStatus.BAD_REQUEST,
				"PHOTO_UPLOAD_UNSUPPORTED_TYPE",
				"JPG, HEIC 또는 PNG 사진만 업로드할 수 있습니다."
			);
		}
		return normalized;
	}

	private static String sanitizeClientFileName(String clientFileName) {
		String normalized = clientFileName.replace('\\', '/');
		String fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
			.replaceAll("[\\p{Cntrl}]", "")
			.trim();
		return fileName.isEmpty() ? "photo" : fileName;
	}

	private static String storageKey(UUID ownerUserId, UUID batchId, UploadItem item) {
		return "temporary/" + ownerUserId + "/" + batchId + "/" + item.getId()
			+ SAFE_EXTENSIONS.get(item.getMimeType());
	}

	private static String ensureStorageKey(UUID ownerUserId, UUID batchId, UploadItem item) {
		if (StringUtils.hasText(item.getTemporaryStorageKey())) {
			return item.getTemporaryStorageKey();
		}
		String storageKey = storageKey(ownerUserId, batchId, item);
		item.start(storageKey);
		return storageKey;
	}

	private static PhotoUploadException invalidState(String code, String message) {
		return new PhotoUploadException(HttpStatus.CONFLICT, code, message);
	}
}
