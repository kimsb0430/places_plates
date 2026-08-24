package com.placesplates.domain.photo.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.placesplates.domain.photo.entity.ImageProcessingJob;
import com.placesplates.domain.photo.entity.ImageProcessingJobStatus;

import jakarta.persistence.LockModeType;

public interface ImageProcessingJobRepository extends JpaRepository<ImageProcessingJob, UUID> {

	Optional<ImageProcessingJob> findByUploadItemId(UUID uploadItemId);

	Optional<ImageProcessingJob> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

	List<ImageProcessingJob> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

	List<ImageProcessingJob> findAllByOwnerUserIdAndPostIdOrderByCreatedAtDesc(
		UUID ownerUserId,
		UUID postId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select job
		from ImageProcessingJob job
		where job.ownerUserId = :ownerUserId
		  and job.status in :statuses
		  and job.nextAttemptAt <= :now
		  and job.attemptCount < job.maxAttempts
		order by job.nextAttemptAt, job.createdAt
		""")
	List<ImageProcessingJob> findReadyJobs(
		@Param("ownerUserId") UUID ownerUserId,
		@Param("statuses") Set<ImageProcessingJobStatus> statuses,
		@Param("now") OffsetDateTime now,
		Pageable pageable
	);
}
