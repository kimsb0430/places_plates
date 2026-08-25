package com.placesplates.domain.photo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.photo.entity.UploadItem;

public interface UploadItemRepository extends JpaRepository<UploadItem, UUID> {

	Optional<UploadItem> findByIdAndUploadBatchIdAndUploadBatchOwnerUserId(
		UUID id,
		UUID uploadBatchId,
		UUID ownerUserId
	);
}
