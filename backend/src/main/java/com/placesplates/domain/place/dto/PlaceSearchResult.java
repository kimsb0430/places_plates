package com.placesplates.domain.place.dto;

import java.math.BigDecimal;

public record PlaceSearchResult(
	String googlePlaceId,
	String name,
	String placeType,
	String formattedAddress,
	BigDecimal latitude,
	BigDecimal longitude,
	String googleMapsUrl
) {
}
