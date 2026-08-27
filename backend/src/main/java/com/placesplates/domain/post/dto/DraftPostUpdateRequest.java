package com.placesplates.domain.post.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DraftPostUpdateRequest(
	@NotBlank
	@Size(max = 200)
	String title,

	@Size(max = 500)
	String summary,

	@Size(max = 50000)
	String content,

	@Min(1000)
	@Max(9999)
	Integer publicVisitYear,

	@Min(1)
	@Max(12)
	Integer publicVisitMonth,

	@Valid
	RestaurantDetailUpdateRequest restaurantDetails,

	@Valid
	DestinationDetailUpdateRequest destinationDetails
) {
}
