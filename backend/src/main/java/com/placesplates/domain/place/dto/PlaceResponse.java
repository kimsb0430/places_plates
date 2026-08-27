package com.placesplates.domain.place.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.place.entity.Place;

public record PlaceResponse(
	UUID id,
	String source,
	String googlePlaceId,
	String name,
	String placeType,
	String formattedAddress,
	BigDecimal latitude,
	BigDecimal longitude,
	String googleMapsUrl,
	OffsetDateTime refreshedAt
) {
	public static PlaceResponse from(Place place) {
		return new PlaceResponse(
			place.getId(), place.getSource().name(), place.getGooglePlaceId(), place.getName(),
			place.getPlaceType(), place.getFormattedAddress(), place.getLatitude(), place.getLongitude(),
			place.getGoogleMapsUrl(), place.getRefreshedAt()
		);
	}
}
