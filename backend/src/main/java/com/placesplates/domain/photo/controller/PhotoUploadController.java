package com.placesplates.domain.photo.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.photo.dto.CreateUploadBatchRequest;
import com.placesplates.domain.photo.dto.UploadBatchResponse;
import com.placesplates.domain.photo.dto.UploadFailureRequest;
import com.placesplates.domain.photo.dto.UploadItemResponse;
import com.placesplates.domain.photo.dto.UploadProgressRequest;
import com.placesplates.domain.photo.service.PhotoUploadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/manage/photo-uploads")
public class PhotoUploadController {

	private final PhotoUploadService photoUploadService;

	public PhotoUploadController(PhotoUploadService photoUploadService) {
		this.photoUploadService = photoUploadService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UploadBatchResponse createBatch(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@Valid @RequestBody CreateUploadBatchRequest request
	) {
		return photoUploadService.createBatch(principal.userId(), request);
	}

	@GetMapping("/{batchId}")
	public UploadBatchResponse getBatch(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID batchId
	) {
		return photoUploadService.getBatch(principal.userId(), batchId);
	}

	@PostMapping("/{batchId}/items/{itemId}/progress")
	public UploadItemResponse recordProgress(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID batchId,
		@PathVariable UUID itemId,
		@Valid @RequestBody UploadProgressRequest request
	) {
		return photoUploadService.recordProgress(
			principal.userId(),
			batchId,
			itemId,
			request.uploadedBytes()
		);
	}

	@PostMapping("/{batchId}/items/{itemId}/failure")
	public UploadItemResponse markFailed(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID batchId,
		@PathVariable UUID itemId,
		@Valid @RequestBody UploadFailureRequest request
	) {
		return photoUploadService.markFailed(principal.userId(), batchId, itemId, request.failureCode());
	}

	@PostMapping("/{batchId}/items/{itemId}/retry")
	public UploadItemResponse retry(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID batchId,
		@PathVariable UUID itemId
	) {
		return photoUploadService.retry(principal.userId(), batchId, itemId);
	}

	@PostMapping("/{batchId}/items/{itemId}/complete")
	public UploadItemResponse complete(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID batchId,
		@PathVariable UUID itemId
	) {
		return photoUploadService.complete(principal.userId(), batchId, itemId);
	}
}
