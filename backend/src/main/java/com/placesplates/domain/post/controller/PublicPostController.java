package com.placesplates.domain.post.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.post.dto.PublicPostListResponse;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.service.PublicPostService;

@RestController
@RequestMapping("/api/v1/public/posts")
public class PublicPostController {

	private final PublicPostService publicPostService;

	public PublicPostController(PublicPostService publicPostService) {
		this.publicPostService = publicPostService;
	}

	@GetMapping
	public PublicPostListResponse getPosts(
		@RequestParam(required = false) PostCategory category
	) {
		return publicPostService.findPublicPosts(category);
	}
}
