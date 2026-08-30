package com.placesplates.domain.photo.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.placesplates.domain.photo.entity.UploadBatch;

import jakarta.persistence.LockModeType;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, UUID> {

	@EntityGraph(attributePaths = "items")
	Optional<UploadBatch> findWithItemsByIdAndOwnerUserId(UUID id, UUID ownerUserId);

	@EntityGraph(attributePaths = "items")
	List<UploadBatch> findAllByPostIdAndOwnerUserId(UUID postId, UUID ownerUserId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select batch
		from UploadBatch batch
		where batch.id = :id and batch.ownerUserId = :ownerUserId
		""")
	Optional<UploadBatch> findLockedWithItemsByIdAndOwnerUserId(
		@Param("id") UUID id,
		@Param("ownerUserId") UUID ownerUserId
	);
}
