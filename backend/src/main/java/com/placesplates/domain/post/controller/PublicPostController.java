package com.placesplates.domain.post.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.post.dto.PublicPostListResponse;
import com.placesplates.domain.post.dto.PublicPostCoverContent;
import com.placesplates.domain.post.dto.PublicPostDetailResponse;
import com.placesplates.domain.post.dto.PublicPostPhotoContent;
import com.placesplates.domain.post.dto.PublicPostSort;
import com.placesplates.domain.post.dto.PublicPlaceHistoryResponse;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.service.PublicPostService;

@RestController
@RequestMapping("/api/v1/public/posts")
public class PublicPostController {
	private static final String PUBLIC_IMAGE_CSP = "default-src 'none'; frame-ancestors 'none'; sandbox";

	private final PublicPostService publicPostService;

	public PublicPostController(PublicPostService publicPostService) {
		this.publicPostService = publicPostService;
	}

	@GetMapping
	public PublicPostListResponse getPosts(
		@RequestParam(required = false) PostCategory category,
		@RequestParam(defaultValue = "LATEST") PublicPostSort sort
	) {
		return publicPostService.findPublicPosts(category, sort);
	}

	@GetMapping("/{postId}")
	public PublicPostDetailResponse getPost(@PathVariable UUID postId) {
		return publicPostService.findPublicPost(postId);
	}

	@GetMapping("/{postId}/place")
	public PublicPlaceHistoryResponse getPlaceHistory(@PathVariable UUID postId) {
		return publicPostService.findPublicPlaceHistory(postId);
	}

	@GetMapping("/{postId}/cover")
	public ResponseEntity<byte[]> getCover(@PathVariable UUID postId) {
		PublicPostCoverContent content = publicPostService.findPublicCover(postId);
		return publicImageResponse(content.bytes(), content.mimeType(), "places-plates-cover.jpg");
	}

	@GetMapping("/{postId}/photos/{photoId}")
	public ResponseEntity<byte[]> getPhoto(
		@PathVariable UUID postId,
		@PathVariable UUID photoId
	) {
		PublicPostPhotoContent content = publicPostService.findPublicDetailPhoto(postId, photoId);
		return publicImageResponse(content.bytes(), content.mimeType(), "places-plates-photo.jpg");
	}

	private ResponseEntity<byte[]> publicImageResponse(byte[] bytes, String mimeType, String filename) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
			.header("Content-Disposition", "inline; filename=\"%s\"".formatted(filename))
			.header("Cross-Origin-Resource-Policy", "same-origin")
			.header("Content-Security-Policy", PUBLIC_IMAGE_CSP)
			.header("X-Frame-Options", "DENY")
			.header("X-Content-Type-Options", "nosniff")
			.contentType(MediaType.parseMediaType(mimeType))
			.body(bytes);
	}
}
