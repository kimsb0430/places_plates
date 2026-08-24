package com.placesplates.domain.photo.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.dto.ImageProcessingJobResponse;
import com.placesplates.domain.photo.entity.ImageProcessingJob;
import com.placesplates.domain.photo.entity.ImageProcessingJobStatus;
import com.placesplates.domain.photo.exception.ImageProcessingJobException;
import com.placesplates.domain.photo.repository.ImageProcessingJobRepository;

@Service
@Transactional(readOnly = true)
public class ImageProcessingJobService {

	private static final Set<ImageProcessingJobStatus> READY_STATUSES = Set.of(
		ImageProcessingJobStatus.PENDING,
		ImageProcessingJobStatus.FAILED
	);
	private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(30);
	private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(15);

	private final ImageProcessingJobRepository jobRepository;

	public ImageProcessingJobService(ImageProcessingJobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	@Transactional
	public ImageProcessingJobResponse enqueueIfAbsent(
		UUID ownerUserId,
		UUID postId,
		UUID uploadItemId
	) {
		ImageProcessingJob job = jobRepository.findByUploadItemId(uploadItemId)
			.orElseGet(() -> jobRepository.save(
				ImageProcessingJob.create(ownerUserId, postId, uploadItemId)
			));
		return ImageProcessingJobResponse.from(job);
	}

	public List<ImageProcessingJobResponse> findJobs(UUID ownerUserId, UUID postId) {
		List<ImageProcessingJob> jobs = postId == null
			? jobRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId)
			: jobRepository.findAllByOwnerUserIdAndPostIdOrderByCreatedAtDesc(ownerUserId, postId);
		return jobs.stream().map(ImageProcessingJobResponse::from).toList();
	}

	@Transactional
	public Optional<ImageProcessingJobResponse> claimNextJob(UUID ownerUserId) {
		OffsetDateTime now = now();
		return jobRepository.findReadyJobs(
			ownerUserId,
			READY_STATUSES,
			now,
			PageRequest.of(0, 1)
		).stream().findFirst().map(job -> {
			job.start(now);
			return ImageProcessingJobResponse.from(job);
		});
	}

	@Transactional
	public ImageProcessingJobResponse completeJob(UUID ownerUserId, UUID jobId) {
		ImageProcessingJob job = getOwnedJob(ownerUserId, jobId);
		try {
			job.complete(now());
		} catch (IllegalStateException exception) {
			throw invalidState("IMAGE_PROCESSING_JOB_NOT_PROCESSING", "처리 중인 작업만 완료할 수 있습니다.");
		}
		return ImageProcessingJobResponse.from(job);
	}

	@Transactional
	public ImageProcessingJobResponse failJob(UUID ownerUserId, UUID jobId, String failureCode) {
		ImageProcessingJob job = getOwnedJob(ownerUserId, jobId);
		OffsetDateTime now = now();
		try {
			job.fail(normalizeFailureCode(failureCode), now.plus(retryDelay(job)), now);
		} catch (IllegalStateException exception) {
			throw invalidState("IMAGE_PROCESSING_JOB_NOT_PROCESSING", "처리 중인 작업만 실패 처리할 수 있습니다.");
		}
		return ImageProcessingJobResponse.from(job);
	}

	@Transactional
	public ImageProcessingJobResponse retryNow(UUID ownerUserId, UUID jobId) {
		ImageProcessingJob job = getOwnedJob(ownerUserId, jobId);
		try {
			job.retryNow(now());
		} catch (IllegalStateException exception) {
			throw invalidState("IMAGE_PROCESSING_JOB_NOT_RETRYABLE", "재시도할 수 있는 실패 작업이 아닙니다.");
		}
		return ImageProcessingJobResponse.from(job);
	}

	private ImageProcessingJob getOwnedJob(UUID ownerUserId, UUID jobId) {
		return jobRepository.findByIdAndOwnerUserId(jobId, ownerUserId)
			.orElseThrow(() -> new ImageProcessingJobException(
				HttpStatus.NOT_FOUND,
				"IMAGE_PROCESSING_JOB_NOT_FOUND",
				"이미지 처리 작업을 찾을 수 없습니다."
			));
	}

	private static Duration retryDelay(ImageProcessingJob job) {
		long multiplier = 1L << Math.max(0, job.getAttemptCount() - 1);
		Duration delay = INITIAL_RETRY_DELAY.multipliedBy(multiplier);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	private static String normalizeFailureCode(String failureCode) {
		if (failureCode == null || failureCode.isBlank()) {
			return "UNKNOWN_ERROR";
		}
		String normalized = failureCode.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
		return normalized.substring(0, Math.min(normalized.length(), 80));
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	private static ImageProcessingJobException invalidState(String code, String message) {
		return new ImageProcessingJobException(HttpStatus.CONFLICT, code, message);
	}
}
