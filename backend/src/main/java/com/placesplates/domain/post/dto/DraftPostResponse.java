package com.placesplates.domain.post.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.place.dto.PlaceResponse;

public record DraftPostResponse(
	UUID id,
	String category,
	String title,
	String summary,
	String content,
	Integer publicVisitYear,
	Integer publicVisitMonth,
	PlaceResponse place,
	RestaurantDetailResponse restaurantDetails,
	DestinationDetailResponse destinationDetails,
	String visibility,
	String status,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
) {
	public static DraftPostResponse from(
		DraftPost draft,
		PlaceResponse place,
		RestaurantDetailResponse restaurantDetails,
		DestinationDetailResponse destinationDetails
	) {
		return new DraftPostResponse(
			draft.getId(),
			draft.getCategory().name(),
			draft.getTitle(),
			draft.getSummary(),
			draft.getContent(),
			draft.getPublicVisitYear() == null ? null : draft.getPublicVisitYear().intValue(),
			draft.getPublicVisitMonth() == null ? null : draft.getPublicVisitMonth().intValue(),
			place,
			restaurantDetails,
			destinationDetails,
			draft.getVisibility().name(),
			draft.getStatus().name(),
			draft.getCreatedAt(),
			draft.getUpdatedAt()
		);
	}
}
