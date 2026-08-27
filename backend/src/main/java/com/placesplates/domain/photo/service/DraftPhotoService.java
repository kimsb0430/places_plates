package com.placesplates.domain.photo.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.dto.DraftPhotoContent;
import com.placesplates.domain.photo.dto.DraftPhotoEditItemRequest;
import com.placesplates.domain.photo.dto.DraftPhotoEditRequest;
import com.placesplates.domain.photo.dto.DraftPhotoResponse;
import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.exception.DraftPhotoException;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.StorageAccessException;

@Service
@Transactional(readOnly = true)
public class DraftPhotoService {

	private final DraftPostRepository draftPostRepository;
	private final PhotoRepository photoRepository;
	private final PhotoAssetRepository photoAssetRepository;
	private final PrivatePhotoStorage privatePhotoStorage;

	public DraftPhotoService(
		DraftPostRepository draftPostRepository,
		PhotoRepository photoRepository,
		PhotoAssetRepository photoAssetRepository,
		PrivatePhotoStorage privatePhotoStorage
	) {
		this.draftPostRepository = draftPostRepository;
		this.photoRepository = photoRepository;
		this.photoAssetRepository = photoAssetRepository;
		this.privatePhotoStorage = privatePhotoStorage;
	}

	public List<DraftPhotoResponse> findPhotos(UUID ownerUserId, UUID draftId) {
		ensureOwnedDraft(ownerUserId, draftId);
		List<Photo> photos = photoRepository
			.findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(draftId, ownerUserId);
		return responses(draftId, photos);
	}

	/**
	 * 送信された配列順を表示順として扱い、代表写真と代替テキストを一括更新する。
	 */
	@Transactional
	public List<DraftPhotoResponse> updatePhotos(
		UUID ownerUserId,
		UUID draftId,
		DraftPhotoEditRequest request
	) {
		ensureOwnedDraft(ownerUserId, draftId);
		List<Photo> photos = photoRepository.findAllForUpdate(draftId, ownerUserId);
		validateCompletePhotoSet(photos, request.photos());

		Map<UUID, Photo> photosById = new HashMap<>();
		for (Photo photo : photos) {
			photosById.put(photo.getId(), photo);
			photo.updateEditorState(photo.getDisplayOrder(), false, photo.getAltText());
		}
		photoRepository.flush();

		for (int index = 0; index < request.photos().size(); index++) {
			DraftPhotoEditItemRequest item = request.photos().get(index);
			photosById.get(item.photoId()).updateEditorState(index, item.cover(), item.altText());
		}
		photoRepository.flush();
		return responses(draftId, request.photos().stream()
			.map(item -> photosById.get(item.photoId()))
			.toList());
	}

	public DraftPhotoContent getThumbnail(UUID ownerUserId, UUID draftId, UUID photoId) {
		ensureOwnedDraft(ownerUserId, draftId);
		photoRepository.findByIdAndPostIdAndOwnerUserId(photoId, draftId, ownerUserId)
			.orElseThrow(() -> notFound("사진을 찾을 수 없습니다."));
		PhotoAsset asset = photoAssetRepository
			.findByPhotoIdAndVariantType(photoId, PhotoAssetVariantType.THUMBNAIL)
			.orElseThrow(() -> notFound("사진 미리보기를 아직 준비하지 못했습니다."));
		try {
			return new DraftPhotoContent(
				privatePhotoStorage.downloadResponsiveVariant(asset.getStorageKey()),
				asset.getMimeType()
			);
		} catch (StorageAccessException exception) {
			throw new DraftPhotoException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"DRAFT_PHOTO_STORAGE_UNAVAILABLE",
				"사진 미리보기를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
			);
		}
	}

	private List<DraftPhotoResponse> responses(UUID draftId, List<Photo> photos) {
		if (photos.isEmpty()) {
			return List.of();
		}
		Set<UUID> thumbnailPhotoIds = photoAssetRepository.findAllByPhotoIdInAndVariantType(
			photos.stream().map(Photo::getId).toList(),
			PhotoAssetVariantType.THUMBNAIL
		).stream().map(PhotoAsset::getPhotoId).collect(java.util.stream.Collectors.toSet());
		return photos.stream()
			.map(photo -> DraftPhotoResponse.from(
				photo,
				draftId,
				thumbnailPhotoIds.contains(photo.getId())
			))
			.toList();
	}

	private void ensureOwnedDraft(UUID ownerUserId, UUID draftId) {
		draftPostRepository.findByIdAndOwnerUserIdAndStatus(draftId, ownerUserId, PostStatus.DRAFT)
			.orElseThrow(() -> notFound("사진을 편집할 초안을 찾을 수 없습니다."));
	}

	private static void validateCompletePhotoSet(
		List<Photo> photos,
		List<DraftPhotoEditItemRequest> requestedPhotos
	) {
		Set<UUID> actualIds = photos.stream().map(Photo::getId).collect(java.util.stream.Collectors.toSet());
		Set<UUID> requestedIds = new HashSet<>();
		long coverCount = 0;
		for (DraftPhotoEditItemRequest requestedPhoto : requestedPhotos) {
			if (!requestedIds.add(requestedPhoto.photoId())) {
				throw invalidRequest("같은 사진을 두 번 포함할 수 없습니다.");
			}
			if (requestedPhoto.cover()) {
				coverCount++;
			}
		}
		if (!actualIds.equals(requestedIds)) {
			throw invalidRequest("초안의 모든 사진을 빠짐없이 포함해주세요.");
		}
		if (coverCount > 1) {
			throw invalidRequest("대표 사진은 한 장만 선택할 수 있습니다.");
		}
	}

	private static DraftPhotoException notFound(String message) {
		return new DraftPhotoException(HttpStatus.NOT_FOUND, "DRAFT_PHOTO_NOT_FOUND", message);
	}

	private static DraftPhotoException invalidRequest(String message) {
		return new DraftPhotoException(HttpStatus.CONFLICT, "DRAFT_PHOTO_INVALID_SET", message);
	}
}
