package com.placesplates.domain.map.dto;

public record MapPostCountsResponse(
	long all,
	long restaurant,
	long destination
) {
}
