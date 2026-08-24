package com.placesplates.domain.photo.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.photo.entity.ImageProcessingJob;

public record ImageProcessingJobResponse(
	UUID id,
	UUID draftPostId,
	UUID uploadItemId,
	String status,
	int attemptCount,
	int maxAttempts,
	boolean canRetry,
	OffsetDateTime nextAttemptAt,
	String lastFailureCode,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
) {
	public static ImageProcessingJobResponse from(ImageProcessingJob job) {
		return new ImageProcessingJobResponse(
			job.getId(),
			job.getPostId(),
			job.getUploadItemId(),
			job.getStatus().name(),
			job.getAttemptCount(),
			job.getMaxAttempts(),
			job.canRetry(),
			job.getNextAttemptAt(),
			job.getLastFailureCode(),
			job.getCreatedAt(),
			job.getUpdatedAt()
		);
	}
}
