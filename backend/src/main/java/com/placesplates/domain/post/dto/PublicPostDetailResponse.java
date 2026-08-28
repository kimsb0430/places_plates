package com.placesplates.domain.post.dto;

import java.util.List;
import java.util.UUID;

import com.placesplates.domain.post.entity.DraftPost;

public record PublicPostDetailResponse(
	UUID id,
	String category,
	String title,
	String summary,
	String content,
	Integer publicVisitYear,
	Integer publicVisitMonth,
	PublicPostPlaceResponse place,
	RestaurantDetailResponse restaurantDetails,
	DestinationDetailResponse destinationDetails,
	List<PublicPostPhotoResponse> photos
) {

	public static PublicPostDetailResponse from(
		DraftPost post,
		PublicPostPlaceResponse place,
		RestaurantDetailResponse restaurantDetails,
		DestinationDetailResponse destinationDetails,
		List<PublicPostPhotoResponse> photos
	) {
		return new PublicPostDetailResponse(
			post.getId(),
			post.getCategory().name(),
			post.getTitle(),
			post.getSummary(),
			post.getContent(),
			post.getPublicVisitYear() == null ? null : post.getPublicVisitYear().intValue(),
			post.getPublicVisitMonth() == null ? null : post.getPublicVisitMonth().intValue(),
			place,
			restaurantDetails,
			destinationDetails,
			photos
		);
	}
}
