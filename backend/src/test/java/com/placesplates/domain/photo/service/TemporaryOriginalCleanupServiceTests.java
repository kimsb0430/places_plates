package com.placesplates.domain.photo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.entity.UploadItemStatus;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadItemRepository;
import com.placesplates.global.security.DatabaseOwnerScope;
import com.placesplates.infra.storage.PrivatePhotoStorage;

@ExtendWith(MockitoExtension.class)
class TemporaryOriginalCleanupServiceTests {

	@Mock
	private DatabaseOwnerScope databaseOwnerScope;
	@Mock
	private UploadItemRepository uploadItemRepository;
	@Mock
	private PhotoRepository photoRepository;
	@Mock
	private PhotoAssetVerificationService verificationService;
	@Mock
	private PrivatePhotoStorage photoStorage;

	private TemporaryOriginalCleanupService service;

	@BeforeEach
	void setUp() {
		service = new TemporaryOriginalCleanupService(
			databaseOwnerScope,
			uploadItemRepository,
			photoRepository,
			verificationService,
			photoStorage
		);
	}

	@Test
	void verifiesProcessedPhotoAndDeletesTemporaryOriginal() {
		UUID ownerUserId = UUID.randomUUID();
		Photo photo = Photo.processing(ownerUserId, UUID.randomUUID());
		UploadItem item = uploadedItem(OffsetDateTime.now().plusHours(1));
		item.assignResultPhoto(photo.getId());
		when(uploadItemRepository.findProcessedOriginalsPendingDeletion(
			eq(ownerUserId), any(Pageable.class)
		)).thenReturn(List.of(item));
		when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
		when(uploadItemRepository.findExpiredOriginalsPendingDeletion(
			eq(ownerUserId), any(OffsetDateTime.class), any(Pageable.class)
		)).thenReturn(List.of());

		int purged = service.purgeOwner(ownerUserId, 10);

		assertThat(purged).isOne();
		assertThat(item.getStatus()).isEqualTo(UploadItemStatus.COMPLETED);
		assertThat(item.getTemporaryStorageKey()).isNull();
		assertThat(item.getOriginalDeletedAt()).isNotNull();
		assertThat(photo.getProcessingStatus()).isEqualTo(PhotoProcessingStatus.READY);
		verify(databaseOwnerScope).activateOwner(ownerUserId);
		verify(verificationService).verifyAndPublish(photo.getId());
		verify(photoStorage).deleteTemporary("temporary/test.jpg");
	}

	@Test
	void deletesExpiredUnprocessedOriginal() {
		UUID ownerUserId = UUID.randomUUID();
		UploadItem item = uploadedItem(OffsetDateTime.now().minusMinutes(1));
		when(uploadItemRepository.findProcessedOriginalsPendingDeletion(
			eq(ownerUserId), any(Pageable.class)
		)).thenReturn(List.of());
		when(uploadItemRepository.findExpiredOriginalsPendingDeletion(
			eq(ownerUserId), any(OffsetDateTime.class), any(Pageable.class)
		)).thenReturn(List.of(item));

		int purged = service.purgeOwner(ownerUserId, 10);

		assertThat(purged).isOne();
		assertThat(item.getStatus()).isEqualTo(UploadItemStatus.EXPIRED);
		assertThat(item.getTemporaryStorageKey()).isNull();
		assertThat(item.getOriginalDeletedAt()).isNotNull();
		verify(photoStorage).deleteTemporary("temporary/test.jpg");
	}

	private static UploadItem uploadedItem(OffsetDateTime expiresAt) {
		UploadItem item = UploadItem.create("photo.jpg", "image/jpeg", 100, expiresAt);
		item.start("temporary/test.jpg");
		item.recordProgress(100);
		item.markUploaded();
		return item;
	}
}
