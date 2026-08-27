package com.placesplates.domain.place.service;

import java.util.List;

import com.placesplates.domain.place.dto.PlaceSearchResult;

public interface PlaceSearchGateway {

	List<PlaceSearchResult> search(String query);
}
