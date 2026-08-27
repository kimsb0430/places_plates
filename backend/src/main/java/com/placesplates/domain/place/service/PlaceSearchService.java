package com.placesplates.domain.place.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.placesplates.domain.place.dto.PlaceSearchResult;
import com.placesplates.domain.place.exception.PlaceException;

@Service
public class PlaceSearchService {

	private final PlaceSearchGateway placeSearchGateway;

	public PlaceSearchService(PlaceSearchGateway placeSearchGateway) {
		this.placeSearchGateway = placeSearchGateway;
	}

	public List<PlaceSearchResult> search(String query) {
		String normalized = query == null ? "" : query.trim();
		if (normalized.length() < 2 || normalized.length() > 100) {
			throw new PlaceException(
				HttpStatus.BAD_REQUEST,
				"PLACE_SEARCH_QUERY_INVALID",
				"장소 검색어를 2자 이상 100자 이하로 입력해주세요."
			);
		}
		return placeSearchGateway.search(normalized);
	}
}
