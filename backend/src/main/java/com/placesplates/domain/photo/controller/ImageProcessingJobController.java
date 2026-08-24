package com.placesplates.domain.photo.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.photo.dto.ImageProcessingJobResponse;
import com.placesplates.domain.photo.service.ImageProcessingJobService;

@RestController
@RequestMapping("/api/v1/manage/image-processing-jobs")
public class ImageProcessingJobController {

	private final ImageProcessingJobService jobService;

	public ImageProcessingJobController(ImageProcessingJobService jobService) {
		this.jobService = jobService;
	}

	@GetMapping
	public List<ImageProcessingJobResponse> getJobs(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@RequestParam(required = false) UUID draftPostId
	) {
		return jobService.findJobs(principal.userId(), draftPostId);
	}

	@PostMapping("/{jobId}/retry")
	public ImageProcessingJobResponse retryJob(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID jobId
	) {
		return jobService.retryNow(principal.userId(), jobId);
	}
}
