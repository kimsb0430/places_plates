package com.placesplates.domain.post.dto;

import java.math.BigDecimal;

import com.placesplates.domain.post.entity.RestaurantDetail;

public record RestaurantDetailResponse(
	BigDecimal rating,
	String recommendedMenu,
	String priceRange,
	Integer waitingMinutes,
	String revisitIntention
) {
	public static RestaurantDetailResponse from(RestaurantDetail detail) {
		return new RestaurantDetailResponse(
			detail.getRating(),
			detail.getRecommendedMenu(),
			detail.getPriceRange() == null ? null : detail.getPriceRange().name(),
			detail.getWaitingMinutes(),
			detail.getRevisitIntention() == null ? null : detail.getRevisitIntention().name()
		);
	}
}
