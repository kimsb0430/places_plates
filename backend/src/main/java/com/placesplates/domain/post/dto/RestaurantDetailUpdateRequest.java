package com.placesplates.domain.post.dto;

import java.math.BigDecimal;

import com.placesplates.domain.post.entity.RestaurantPriceRange;
import com.placesplates.domain.post.entity.RevisitIntention;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RestaurantDetailUpdateRequest(
	@DecimalMin("0.0")
	@DecimalMax("5.0")
	@Digits(integer = 1, fraction = 1)
	BigDecimal rating,

	@Size(max = 300)
	String recommendedMenu,

	RestaurantPriceRange priceRange,

	@PositiveOrZero
	Integer waitingMinutes,

	RevisitIntention revisitIntention
) {
	public boolean isEmpty() {
		return rating == null
			&& (recommendedMenu == null || recommendedMenu.isBlank())
			&& priceRange == null
			&& waitingMinutes == null
			&& revisitIntention == null;
	}
}
