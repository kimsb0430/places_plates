package com.placesplates.domain.post.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.entity.UploadBatch;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadBatchRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.exception.DraftPostException;
import com.placesplates.domain.post.repository.DestinationDetailRepository;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.post.repository.RestaurantDetailRepository;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.StorageAccessException;

class PostManagementServiceTests {

	private DraftPostRepository postRepository;
	private UploadBatchRepository uploadBatchRepository;
	private PhotoRepository photoRepository;
	private PhotoAssetRepository photoAssetRepository;
	private PrivatePhotoStorage photoStorage;
	private PostManagementService service;

	@BeforeEach
	void setUp() {
		postRepository = mock(DraftPostRepository.class);
		uploadBatchRepository = mock(UploadBatchRepository.class);
		photoRepository = mock(PhotoRepository.class);
		photoAssetRepository = mock(PhotoAssetRepository.class);
		photoStorage = mock(PrivatePhotoStorage.class);
		service = new PostManagementService(
			postRepository,
			mock(RestaurantDetailRepository.class),
			mock(DestinationDetailRepository.class),
			mock(PlaceRepository.class),
			uploadBatchRepository,
			photoRepository,
			photoAssetRepository,
			photoStorage
		);
	}

	@Test
	void deletesOwnedDraftRowsAndEveryStoredPhotoObject() {
		UUID ownerUserId = UUID.randomUUID();
		DraftPost post = DraftPost.create(ownerUserId, PostCategory.DESTINATION);
		UploadBatch batch = UploadBatch.create(ownerUserId, OffsetDateTime.now().plusHours(1));
		batch.assignPost(post.getId());
		UploadItem item = UploadItem.create("photo.jpg", "image/jpeg", 100L, OffsetDateTime.now().plusHours(1));
		item.start("temporary/owner/photo.jpg");
		batch.addItem(item);
		Photo photo = Photo.processing(ownerUserId, post.getId());
		PhotoAsset asset = PhotoAsset.privateResponsiveVariant(
			photo.getId(),
			PhotoAssetVariantType.PUBLIC_DETAIL,
			"variants/owner/job/public-detail.jpg",
			"image/jpeg",
			1600,
			900,
			100L
		);
		when(postRepository.findByIdAndOwnerUserIdAndStatus(post.getId(), ownerUserId, PostStatus.DRAFT))
			.thenReturn(Optional.of(post));
		when(uploadBatchRepository.findAllByPostIdAndOwnerUserId(post.getId(), ownerUserId))
			.thenReturn(List.of(batch));
		when(photoRepository.findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(
			post.getId(), ownerUserId
		)).thenReturn(List.of(photo));
		when(photoAssetRepository.findAllByPhotoIdIn(List.of(photo.getId()))).thenReturn(List.of(asset));

		service.deleteDraft(ownerUserId, post.getId());

		verify(photoStorage).deleteTemporary("temporary/owner/photo.jpg");
		verify(photoStorage).deleteSanitizedAsset("variants/owner/job/public-detail.jpg");
		verify(uploadBatchRepository).deleteAll(List.of(batch));
		verify(photoRepository).deleteAll(List.of(photo));
		verify(postRepository).delete(post);
	}

	@Test
	void keepsDatabaseRowsWhenStorageCleanupFails() {
		UUID ownerUserId = UUID.randomUUID();
		DraftPost post = DraftPost.create(ownerUserId, PostCategory.RESTAURANT);
		Photo photo = Photo.processing(ownerUserId, post.getId());
		PhotoAsset asset = PhotoAsset.sanitizedMaster(
			photo.getId(), "sanitized/owner/master.jpg", "image/jpeg", 100, 100, 50L
		);
		when(postRepository.findByIdAndOwnerUserIdAndStatus(post.getId(), ownerUserId, PostStatus.DRAFT))
			.thenReturn(Optional.of(post));
		when(uploadBatchRepository.findAllByPostIdAndOwnerUserId(post.getId(), ownerUserId))
			.thenReturn(List.of());
		when(photoRepository.findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(
			post.getId(), ownerUserId
		)).thenReturn(List.of(photo));
		when(photoAssetRepository.findAllByPhotoIdIn(List.of(photo.getId()))).thenReturn(List.of(asset));
		doThrow(new StorageAccessException("unavailable"))
			.when(photoStorage).deleteSanitizedAsset(asset.getStorageKey());

		assertThatThrownBy(() -> service.deleteDraft(ownerUserId, post.getId()))
			.isInstanceOf(DraftPostException.class)
			.hasMessageContaining("사진 저장소");
		verify(postRepository, never()).delete(post);
		verify(photoRepository, never()).deleteAll(List.of(photo));
	}

	@Test
	void deletesPublishedPostThroughPublishedStateBoundary() {
		UUID ownerUserId = UUID.randomUUID();
		DraftPost post = DraftPost.create(ownerUserId, PostCategory.DESTINATION);
		post.publish(PostVisibility.PUBLIC);
		when(postRepository.findByIdAndOwnerUserIdAndStatus(post.getId(), ownerUserId, PostStatus.PUBLISHED))
			.thenReturn(Optional.of(post));
		when(uploadBatchRepository.findAllByPostIdAndOwnerUserId(post.getId(), ownerUserId))
			.thenReturn(List.of());
		when(photoRepository.findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(
			post.getId(), ownerUserId
		)).thenReturn(List.of());
		when(photoAssetRepository.findAllByPhotoIdIn(List.of())).thenReturn(List.of());

		service.deletePublishedPost(ownerUserId, post.getId());

		verify(postRepository).delete(post);
		verify(postRepository, never()).findByIdAndOwnerUserIdAndStatus(
			post.getId(), ownerUserId, PostStatus.DRAFT
		);
	}
}
