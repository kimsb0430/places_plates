package com.placesplates.domain.place.dto;

import java.math.BigDecimal;

import com.placesplates.domain.place.entity.PlaceSource;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceConnectionRequest(
	@NotNull PlaceSource source,
	@Size(max = 255) String googlePlaceId,
	@NotBlank @Size(max = 200) String name,
	@Size(max = 80) String placeType,
	@Size(max = 500) String formattedAddress,
	@DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
	@DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
) {
}
