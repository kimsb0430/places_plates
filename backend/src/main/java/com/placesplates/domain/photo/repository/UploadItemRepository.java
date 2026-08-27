package com.placesplates.domain.photo.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.placesplates.domain.photo.entity.UploadItem;

import jakarta.persistence.LockModeType;

public interface UploadItemRepository extends JpaRepository<UploadItem, UUID> {

	List<UploadItem> findAllByResultPhotoIdIn(List<UUID> resultPhotoIds);

	Optional<UploadItem> findByIdAndUploadBatchIdAndUploadBatchOwnerUserId(
		UUID id,
		UUID uploadBatchId,
		UUID ownerUserId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select item
		from UploadItem item
		where item.uploadBatch.ownerUserId = :ownerUserId
		  and item.resultPhotoId is not null
		  and item.temporaryStorageKey is not null
		  and item.originalDeletedAt is null
		order by item.updatedAt, item.createdAt
		""")
	List<UploadItem> findProcessedOriginalsPendingDeletion(
		@Param("ownerUserId") UUID ownerUserId,
		Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select item
		from UploadItem item
		where item.uploadBatch.ownerUserId = :ownerUserId
		  and item.resultPhotoId is null
		  and item.originalDeletedAt is null
		  and item.expiresAt <= :now
		order by item.expiresAt, item.createdAt
		""")
	List<UploadItem> findExpiredOriginalsPendingDeletion(
		@Param("ownerUserId") UUID ownerUserId,
		@Param("now") OffsetDateTime now,
		Pageable pageable
	);
}
