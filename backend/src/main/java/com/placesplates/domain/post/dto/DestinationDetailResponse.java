package com.placesplates.domain.post.dto;

import com.placesplates.domain.post.entity.DestinationDetail;

public record DestinationDetailResponse(
	String recommendedTime,
	Integer durationMinutes,
	String highlights,
	String travelTips
) {
	public static DestinationDetailResponse from(DestinationDetail detail) {
		return new DestinationDetailResponse(
			detail.getRecommendedTime(),
			detail.getDurationMinutes(),
			detail.getHighlights(),
			detail.getTravelTips()
		);
	}
}
