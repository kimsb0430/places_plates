package com.placesplates.domain.photo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.photo.entity.UploadBatch;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, UUID> {

	@EntityGraph(attributePaths = "items")
	Optional<UploadBatch> findWithItemsByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
