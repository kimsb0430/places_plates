package com.placesplates.domain.post.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DestinationDetailUpdateRequest(
	@Size(max = 100)
	String recommendedTime,

	@PositiveOrZero
	Integer durationMinutes,

	@Size(max = 5000)
	String highlights,

	@Size(max = 5000)
	String travelTips
) {
	public boolean isEmpty() {
		return (recommendedTime == null || recommendedTime.isBlank())
			&& durationMinutes == null
			&& (highlights == null || highlights.isBlank())
			&& (travelTips == null || travelTips.isBlank());
	}
}
