package com.placesplates.domain.post.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.dto.DraftPostUpdateRequest;
import com.placesplates.domain.post.service.DraftPostService;
import com.placesplates.domain.post.service.PostManagementService;
import com.placesplates.domain.place.dto.PlaceConnectionRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/manage/posts")
public class ManagedPostController {

	private final PostManagementService postManagementService;
	private final DraftPostService draftPostService;

	public ManagedPostController(
		PostManagementService postManagementService,
		DraftPostService draftPostService
	) {
		this.postManagementService = postManagementService;
		this.draftPostService = draftPostService;
	}

	@GetMapping("/{postId}")
	public DraftPostResponse getPublishedPost(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId
	) {
		return draftPostService.findPublishedPost(principal.userId(), postId);
	}

	@PatchMapping("/{postId}")
	public DraftPostResponse updatePublishedPost(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId,
		@Valid @RequestBody DraftPostUpdateRequest request
	) {
		return draftPostService.updatePublishedPost(principal.userId(), postId, request);
	}

	@PutMapping("/{postId}/place")
	public DraftPostResponse connectPlace(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId,
		@Valid @RequestBody PlaceConnectionRequest request
	) {
		return draftPostService.connectPublishedPlace(principal.userId(), postId, request);
	}

	@DeleteMapping("/{postId}/place")
	public DraftPostResponse disconnectPlace(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId
	) {
		return draftPostService.disconnectPublishedPlace(principal.userId(), postId);
	}

	@GetMapping
	public List<DraftPostResponse> getPublishedPosts(
		@AuthenticationPrincipal AdministratorPrincipal principal
	) {
		return postManagementService.findPublishedPosts(principal.userId());
	}

	@DeleteMapping("/{postId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePublishedPost(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID postId
	) {
		postManagementService.deletePublishedPost(principal.userId(), postId);
	}
}
