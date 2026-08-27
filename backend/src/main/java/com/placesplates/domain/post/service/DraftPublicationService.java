package com.placesplates.domain.post.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.entity.UploadItemStatus;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadItemRepository;
import com.placesplates.domain.post.dto.PostPublicationCheckResponse;
import com.placesplates.domain.post.dto.PostPublicationReadinessResponse;
import com.placesplates.domain.post.dto.PostPublicationRequest;
import com.placesplates.domain.post.dto.PostPublicationResponse;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.exception.DraftPostException;
import com.placesplates.domain.post.repository.DraftPostRepository;

@Service
@Transactional(readOnly = true)
public class DraftPublicationService {

	private static final String WATERMARK_POSITION = "BOTTOM_RIGHT";

	private final DraftPostRepository draftPostRepository;
	private final PhotoRepository photoRepository;
	private final PhotoAssetRepository photoAssetRepository;
	private final UploadItemRepository uploadItemRepository;
	private final String watermarkVersion;

	public DraftPublicationService(
		DraftPostRepository draftPostRepository,
		PhotoRepository photoRepository,
		PhotoAssetRepository photoAssetRepository,
		UploadItemRepository uploadItemRepository,
		@Value("${places-plates.image.watermark.version:places-plates-corner-v1}") String watermarkVersion
	) {
		this.draftPostRepository = draftPostRepository;
		this.photoRepository = photoRepository;
		this.photoAssetRepository = photoAssetRepository;
		this.uploadItemRepository = uploadItemRepository;
		this.watermarkVersion = watermarkVersion;
	}

	public PostPublicationReadinessResponse getReadiness(UUID ownerUserId, UUID draftId) {
		DraftPost draft = findOwnedDraft(ownerUserId, draftId);
		return assess(draft, ownerUserId);
	}

	@Transactional
	public PostPublicationResponse publish(
		UUID ownerUserId,
		UUID draftId,
		PostPublicationRequest request
	) {
		DraftPost draft = findOwnedDraft(ownerUserId, draftId);
		PostPublicationReadinessResponse readiness = assess(draft, ownerUserId);
		if (!readiness.ready()) {
			throw new DraftPostException(
				HttpStatus.CONFLICT,
				"POST_PUBLICATION_NOT_READY",
				"게시 전 검사를 모두 통과해야 기록을 게시할 수 있습니다."
			);
		}
		draft.publish(request.visibility());
		return PostPublicationResponse.from(draft);
	}

	/**
	 * 保存済み状態だけを信頼し、入力・写真・原本削除・公開派生画像の条件を再評価する。
	 */
	private PostPublicationReadinessResponse assess(DraftPost draft, UUID ownerUserId) {
		List<Photo> photos = photoRepository
			.findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(draft.getId(), ownerUserId);
		List<UUID> photoIds = photos.stream().map(Photo::getId).toList();
		List<PhotoAsset> assets = photoIds.isEmpty()
			? List.of()
			: photoAssetRepository.findAllByPhotoIdIn(photoIds);
		List<UploadItem> uploadItems = photoIds.isEmpty()
			? List.of()
			: uploadItemRepository.findAllByResultPhotoIdIn(photoIds);

		List<PostPublicationCheckResponse> checks = List.of(
			check("TITLE", "제목 입력", draft.getTitle() != null && !draft.getTitle().isBlank()),
			check("VISIT_MONTH", "방문 월 입력", hasVisitMonth(draft)),
			check("SUMMARY", "한줄평 입력", draft.getSummary() != null && !draft.getSummary().isBlank()),
			check("PLACE", "장소 연결", draft.getPlaceId() != null),
			check("PHOTO", "사진 1장 이상", !photos.isEmpty()),
			check("COVER", "대표 사진 1장", photos.stream().filter(Photo::isCover).count() == 1),
			check("PHOTO_READY", "모든 사진 처리 완료", photosReady(photos)),
			check("ORIGINAL_DELETED", "업로드 원본 삭제 완료", originalsDeleted(photoIds, uploadItems)),
			check("PUBLIC_ASSETS", "메타데이터 제거 및 워터마크 확인", assetsSafe(photoIds, assets))
		);
		return new PostPublicationReadinessResponse(
			checks.stream().allMatch(PostPublicationCheckResponse::passed),
			checks
		);
	}

	private boolean assetsSafe(List<UUID> photoIds, List<PhotoAsset> assets) {
		if (photoIds.isEmpty() || assets.size() != photoIds.size() * PhotoAssetVariantType.values().length) {
			return false;
		}
		Map<UUID, List<PhotoAsset>> assetsByPhoto = assets.stream()
			.collect(Collectors.groupingBy(PhotoAsset::getPhotoId));
		return photoIds.stream().allMatch(photoId -> photoAssetsSafe(assetsByPhoto.getOrDefault(photoId, List.of())));
	}

	private boolean photoAssetsSafe(List<PhotoAsset> assets) {
		Map<PhotoAssetVariantType, PhotoAsset> indexed = new EnumMap<>(PhotoAssetVariantType.class);
		for (PhotoAsset asset : assets) {
			if (indexed.put(asset.getVariantType(), asset) != null) {
				return false;
			}
		}
		PhotoAsset master = indexed.get(PhotoAssetVariantType.SANITIZED_MASTER);
		if (master == null
			|| !"PRIVATE".equals(master.getAccessLevel())
			|| !master.isMetadataScanPassed()
			|| master.isWatermarkApplied()) {
			return false;
		}
		return List.of(
			PhotoAssetVariantType.THUMBNAIL,
			PhotoAssetVariantType.MAP_CARD,
			PhotoAssetVariantType.PUBLIC_DETAIL
		).stream().allMatch(type -> {
			PhotoAsset asset = indexed.get(type);
			return asset != null && asset.usesWatermarkPolicy(watermarkVersion, WATERMARK_POSITION);
		});
	}

	private static boolean originalsDeleted(List<UUID> photoIds, List<UploadItem> uploadItems) {
		if (photoIds.isEmpty() || uploadItems.size() != photoIds.size()) {
			return false;
		}
		Map<UUID, UploadItem> itemsByPhoto = uploadItems.stream().collect(Collectors.toMap(
			UploadItem::getResultPhotoId,
			Function.identity(),
			(first, duplicate) -> first
		));
		return itemsByPhoto.size() == photoIds.size() && photoIds.stream().allMatch(photoId -> {
			UploadItem item = itemsByPhoto.get(photoId);
			return item != null
				&& item.getStatus() == UploadItemStatus.COMPLETED
				&& item.getTemporaryStorageKey() == null
				&& item.getOriginalDeletedAt() != null;
		});
	}

	private static boolean photosReady(List<Photo> photos) {
		return !photos.isEmpty()
			&& photos.stream().allMatch(photo -> photo.getProcessingStatus() == PhotoProcessingStatus.READY);
	}

	private static boolean hasVisitMonth(DraftPost draft) {
		return draft.getPublicVisitYear() != null && draft.getPublicVisitMonth() != null;
	}

	private static PostPublicationCheckResponse check(String code, String label, boolean passed) {
		return new PostPublicationCheckResponse(code, label, passed);
	}

	private DraftPost findOwnedDraft(UUID ownerUserId, UUID draftId) {
		return draftPostRepository
			.findByIdAndOwnerUserIdAndStatus(draftId, ownerUserId, PostStatus.DRAFT)
			.orElseThrow(() -> new DraftPostException(
				HttpStatus.NOT_FOUND,
				"DRAFT_POST_NOT_FOUND",
				"작성 중인 초안을 찾을 수 없습니다."
			));
	}
}
