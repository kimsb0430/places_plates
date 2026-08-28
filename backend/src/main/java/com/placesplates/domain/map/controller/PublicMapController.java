package com.placesplates.domain.map.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.map.dto.MapPostListResponse;
import com.placesplates.domain.map.service.PublicMapService;
import com.placesplates.domain.post.entity.PostCategory;

@RestController
@RequestMapping("/api/v1/map")
public class PublicMapController {

	private final PublicMapService publicMapService;

	public PublicMapController(PublicMapService publicMapService) {
		this.publicMapService = publicMapService;
	}

	@GetMapping("/posts")
	public MapPostListResponse getMapPosts(
		@RequestParam(required = false) PostCategory category
	) {
		return publicMapService.findMapPosts(category);
	}
}
