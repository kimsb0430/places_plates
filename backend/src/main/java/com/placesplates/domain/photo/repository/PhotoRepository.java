package com.placesplates.domain.photo.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

	List<Photo> findAllByPostIdAndOwnerUserIdOrderByDisplayOrderAscCreatedAtAsc(
		UUID postId,
		UUID ownerUserId
	);

	Optional<Photo> findByIdAndPostIdAndOwnerUserId(UUID id, UUID postId, UUID ownerUserId);

	List<Photo> findAllByPostIdInAndCoverTrueAndProcessingStatus(
		List<UUID> postIds,
		PhotoProcessingStatus processingStatus
	);

	Optional<Photo> findByPostIdAndCoverTrueAndProcessingStatus(
		UUID postId,
		PhotoProcessingStatus processingStatus
	);

	@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT photo
		FROM Photo photo
		WHERE photo.postId = :postId
		  AND photo.ownerUserId = :ownerUserId
		ORDER BY photo.displayOrder ASC, photo.createdAt ASC
		""")
	List<Photo> findAllForUpdate(
		@Param("postId") UUID postId,
		@Param("ownerUserId") UUID ownerUserId
	);
}
