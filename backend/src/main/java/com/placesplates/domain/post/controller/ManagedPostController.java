package com.placesplates.domain.post.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.service.PostManagementService;

@RestController
@RequestMapping("/api/v1/manage/posts")
public class ManagedPostController {

	private final PostManagementService postManagementService;

	public ManagedPostController(PostManagementService postManagementService) {
		this.postManagementService = postManagementService;
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
