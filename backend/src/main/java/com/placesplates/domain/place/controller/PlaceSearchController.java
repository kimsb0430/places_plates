package com.placesplates.domain.place.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.place.dto.PlaceSearchResult;
import com.placesplates.domain.place.service.PlaceSearchService;

@RestController
@RequestMapping("/api/v1/manage/places")
public class PlaceSearchController {

	private final PlaceSearchService placeSearchService;

	public PlaceSearchController(PlaceSearchService placeSearchService) {
		this.placeSearchService = placeSearchService;
	}

	@GetMapping("/search")
	public List<PlaceSearchResult> search(@RequestParam String query) {
		return placeSearchService.search(query);
	}
}
