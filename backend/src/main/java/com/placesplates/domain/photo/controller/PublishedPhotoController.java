package com.placesplates.domain.photo.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.photo.dto.DraftPhotoContent;
import com.placesplates.domain.photo.dto.DraftPhotoEditRequest;
import com.placesplates.domain.photo.dto.DraftPhotoResponse;
import com.placesplates.domain.photo.service.DraftPhotoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/manage/posts/{postId}/photos")
public class PublishedPhotoController {

	private final DraftPhotoService draftPhotoService;

	public PublishedPhotoController(DraftPhotoService draftPhotoService) {
		this.draftPhotoService = draftPhotoService;
	}

	@GetMapping
	public List<DraftPhotoResponse> getPhotos(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId
	) {
		return draftPhotoService.findPublishedPhotos(principal.userId(), postId);
	}

	@PutMapping
	public List<DraftPhotoResponse> updatePhotos(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId,
		@Valid @RequestBody DraftPhotoEditRequest request
	) {
		return draftPhotoService.updatePublishedPhotos(principal.userId(), postId, request);
	}

	@GetMapping("/{photoId}/thumbnail")
	public ResponseEntity<byte[]> getThumbnail(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId,
		@PathVariable UUID photoId
	) {
		DraftPhotoContent content = draftPhotoService.getPublishedThumbnail(
			principal.userId(), postId, photoId
		);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.contentType(MediaType.parseMediaType(content.mimeType()))
			.body(content.bytes());
	}
}
