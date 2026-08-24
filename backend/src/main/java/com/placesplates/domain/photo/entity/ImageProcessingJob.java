package com.placesplates.domain.photo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "image_processing_jobs")
public class ImageProcessingJob {

	public static final int DEFAULT_MAX_ATTEMPTS = 5;

	@Id
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Column(name = "post_id", nullable = false)
	private UUID postId;

	@Column(name = "upload_item_id", nullable = false, unique = true)
	private UUID uploadItemId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImageProcessingJobStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "next_attempt_at", nullable = false)
	private OffsetDateTime nextAttemptAt;

	@Column(name = "last_failure_code", length = 80)
	private String lastFailureCode;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ImageProcessingJob() {
	}

	private ImageProcessingJob(UUID ownerUserId, UUID postId, UUID uploadItemId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.id = UUID.randomUUID();
		this.ownerUserId = ownerUserId;
		this.postId = postId;
		this.uploadItemId = uploadItemId;
		this.status = ImageProcessingJobStatus.PENDING;
		this.attemptCount = 0;
		this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
		this.nextAttemptAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static ImageProcessingJob create(UUID ownerUserId, UUID postId, UUID uploadItemId) {
		return new ImageProcessingJob(ownerUserId, postId, uploadItemId);
	}

	public void start(OffsetDateTime now) {
		if (!isReady(now)) {
			throw new IllegalStateException("Image processing job is not ready");
		}
		status = ImageProcessingJobStatus.PROCESSING;
		attemptCount++;
		startedAt = now;
		completedAt = null;
		touch(now);
	}

	public void complete(OffsetDateTime now) {
		if (status != ImageProcessingJobStatus.PROCESSING) {
			throw new IllegalStateException("Only processing jobs can be completed");
		}
		status = ImageProcessingJobStatus.COMPLETED;
		completedAt = now;
		lastFailureCode = null;
		touch(now);
	}

	public void fail(String failureCode, OffsetDateTime retryAt, OffsetDateTime now) {
		if (status != ImageProcessingJobStatus.PROCESSING) {
			throw new IllegalStateException("Only processing jobs can fail");
		}
		status = ImageProcessingJobStatus.FAILED;
		lastFailureCode = failureCode;
		nextAttemptAt = retryAt;
		completedAt = null;
		touch(now);
	}

	public void retryNow(OffsetDateTime now) {
		if (status != ImageProcessingJobStatus.FAILED || !canRetry()) {
			throw new IllegalStateException("Image processing job cannot be retried");
		}
		status = ImageProcessingJobStatus.PENDING;
		nextAttemptAt = now;
		startedAt = null;
		touch(now);
	}

	public boolean isReady(OffsetDateTime now) {
		return (status == ImageProcessingJobStatus.PENDING || status == ImageProcessingJobStatus.FAILED)
			&& !nextAttemptAt.isAfter(now)
			&& hasRemainingAttempts();
	}

	public boolean canRetry() {
		return status == ImageProcessingJobStatus.FAILED && hasRemainingAttempts();
	}

	private boolean hasRemainingAttempts() {
		return attemptCount < maxAttempts;
	}

	private void touch(OffsetDateTime now) {
		updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public UUID getPostId() {
		return postId;
	}

	public UUID getUploadItemId() {
		return uploadItemId;
	}

	public ImageProcessingJobStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public OffsetDateTime getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastFailureCode() {
		return lastFailureCode;
	}

	public OffsetDateTime getStartedAt() {
		return startedAt;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
