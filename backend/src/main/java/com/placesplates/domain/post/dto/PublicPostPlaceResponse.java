package com.placesplates.domain.post.dto;

import com.placesplates.domain.place.entity.Place;

public record PublicPostPlaceResponse(
	String name,
	String googleMapsUrl
) {

	public static PublicPostPlaceResponse from(Place place) {
		return new PublicPostPlaceResponse(place.getName(), place.getGoogleMapsUrl());
	}
}
