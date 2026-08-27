package com.placesplates.domain.post.dto;

public record PublicPostCountsResponse(
	long all,
	long restaurant,
	long destination
) {
}
