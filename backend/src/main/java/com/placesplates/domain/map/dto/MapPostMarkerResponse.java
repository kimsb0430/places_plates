package com.placesplates.domain.map.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCoordinateVisibility;

public record MapPostMarkerResponse(
	UUID id,
	String category,
	String title,
	String placeName,
	BigDecimal latitude,
	BigDecimal longitude,
	Integer publicVisitYear,
	Integer publicVisitMonth
) {

	public static MapPostMarkerResponse from(DraftPost post, Place place) {
		return new MapPostMarkerResponse(
			post.getId(),
			post.getCategory().name(),
			post.getTitle(),
			place.getName(),
			visibleCoordinate(place.getLatitude(), post.getCoordinateVisibility()),
			visibleCoordinate(place.getLongitude(), post.getCoordinateVisibility()),
			post.getPublicVisitYear() == null ? null : post.getPublicVisitYear().intValue(),
			post.getPublicVisitMonth() == null ? null : post.getPublicVisitMonth().intValue()
		);
	}

	private static BigDecimal visibleCoordinate(
		BigDecimal coordinate,
		PostCoordinateVisibility visibility
	) {
		return visibility == PostCoordinateVisibility.APPROXIMATE
			? coordinate.setScale(2, RoundingMode.HALF_UP)
			: coordinate;
	}
}
