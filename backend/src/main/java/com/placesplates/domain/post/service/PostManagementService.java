package com.placesplates.domain.post.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.UploadBatch;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadBatchRepository;
import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.exception.DraftPostException;
import com.placesplates.domain.post.repository.DestinationDetailRepository;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.post.repository.RestaurantDetailRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.StorageAccessException;

@Service
@Transactional(readOnly = true)
public class PostManagementService {

	private final DraftPostRepository postRepository;
	private final RestaurantDetailRepository restaurantDetailRepository;
	private final DestinationDetailRepository destinationDetailRepository;
	private final PlaceRepository placeRepository;
	private final UploadBatchRepository uploadBatchRepository;
	private final PhotoRepository photoRepository;
	private final PhotoAssetRepository photoAssetRepository;
	private final PrivatePhotoStorage photoStorage;

	public PostManagementService(
		DraftPostRepository postRepository,
		RestaurantDetailRepository restaurantDetailRepository,
		DestinationDetailRepository destinationDetailRepository,
		PlaceRepository placeRepository,
		UploadBatchRepository uploadBatchRepository,
		PhotoRepository photoRepository,
		PhotoAssetRepository photoAssetRepository,
		PrivatePhotoStorage photoStorage
	) {
		this.postRepository = postRepository;
		this.restaurantDetailRepository = restaurantDetailRepository;
		this.destinationDetailRepository = destinationDetailRepository;
		this.placeRepository = placeRepository;
		this.uploadBatchRepository = uploadBatchRepository;
		this.photoRepository = photoRepository;
		this.photoAssetRepository = photoAssetRepository;
		this.photoStorage = photoStorage;
	}

	public List<DraftPostResponse> findPublishedPosts(UUID ownerUserId) {
		return postRepository.findAllByOwnerUserIdAndStatusOrderByPublishedAtDesc(ownerUserId, PostStatus.PUBLISHED)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public void deleteDraft(UUID ownerUserId, UUID postId) {
		deleteOwnedPost(ownerUserId, postId, PostStatus.DRAFT, "작성 중인 초안을 찾을 수 없습니다.");
	}

	@Transactional
	public void deletePublishedPost(UUID ownerUserId, UUID postId) {
		deleteOwnedPost(ownerUserId, postId, PostStatus.PUBLISHED, "게시 완료 기록을 찾을 수 없습니다.");
	}

	/**
	 * 所有者と状態を検証し、保存オブジェクトを先に消してから投稿関連行を削除する。
	 */
	private void deleteOwnedPost(UUID ownerUserId, UUID postId, PostStatus status, String notFoundMessage) {
		DraftPost post = postRepository.findByIdAndOwnerUserIdAndStatus(postId, ownerUserId, status)
			.orElseThrow(() -> new DraftPostException(
				HttpStatus.NOT_FOUND,
				"MANAGED_POST_NOT_FOUND",
				notFoundMessage
			));
		List<UploadBatch> batches = uploadBatchRepository.findAllByPostIdAndOwnerUserId(postId, ownerUserId);
		List<Photo> photos = photoRepository.findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(
			postId,
			ownerUserId
		);
		List<PhotoAsset> assets = photoAssetRepository.findAllByPhotoIdIn(
			photos.stream().map(Photo::getId).toList()
		);

		try {
			batches.stream()
				.flatMap(batch -> batch.getItems().stream())
				.map(item -> item.getTemporaryStorageKey())
				.filter(key -> key != null && !key.isBlank())
				.distinct()
				.forEach(photoStorage::deleteTemporary);
			assets.stream()
				.map(PhotoAsset::getStorageKey)
				.distinct()
				.forEach(photoStorage::deleteSanitizedAsset);
		} catch (StorageAccessException exception) {
			throw new DraftPostException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"POST_STORAGE_DELETE_FAILED",
				"사진 저장소를 정리하지 못해 기록을 삭제하지 않았습니다. 잠시 후 다시 시도해주세요."
			);
		}

		uploadBatchRepository.deleteAll(batches);
		photoRepository.deleteAll(photos);
		postRepository.delete(post);
	}

	private DraftPostResponse toResponse(DraftPost post) {
		Place place = post.getPlaceId() == null ? null : placeRepository.findById(post.getPlaceId()).orElse(null);
		return DraftPostResponse.from(
			post,
			place == null ? null : com.placesplates.domain.place.dto.PlaceResponse.from(place),
			restaurantDetailRepository.findById(post.getId())
				.map(com.placesplates.domain.post.dto.RestaurantDetailResponse::from).orElse(null),
			destinationDetailRepository.findById(post.getId())
				.map(com.placesplates.domain.post.dto.DestinationDetailResponse::from).orElse(null)
		);
	}
}
