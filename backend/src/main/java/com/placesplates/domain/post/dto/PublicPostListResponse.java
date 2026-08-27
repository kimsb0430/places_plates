package com.placesplates.domain.post.dto;

import java.util.List;

public record PublicPostListResponse(
	PublicPostCountsResponse counts,
	List<PublicPostSummaryResponse> posts
) {
}
