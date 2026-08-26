package com.placesplates.domain.post.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.post.dto.DraftPostResponse;
import com.placesplates.domain.post.dto.DraftPostUpdateRequest;
import com.placesplates.domain.post.service.DraftPostService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/manage/drafts")
public class DraftPostController {

	private final DraftPostService draftPostService;

	public DraftPostController(DraftPostService draftPostService) {
		this.draftPostService = draftPostService;
	}

	@GetMapping
	public List<DraftPostResponse> getDrafts(
		@AuthenticationPrincipal AdministratorPrincipal principal
	) {
		return draftPostService.findDrafts(principal.userId());
	}

	@GetMapping("/{draftId}")
	public DraftPostResponse getDraft(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID draftId
	) {
		return draftPostService.findDraft(principal.userId(), draftId);
	}

	@PatchMapping("/{draftId}")
	public DraftPostResponse updateDraft(
		@AuthenticationPrincipal AdministratorPrincipal principal,
		@PathVariable UUID draftId,
		@Valid @RequestBody DraftPostUpdateRequest request
	) {
		return draftPostService.updateDraft(principal.userId(), draftId, request);
	}
}
