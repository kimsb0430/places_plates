package com.placesplates.domain.map.dto;

import java.util.List;

public record MapPostListResponse(
	MapPostCountsResponse counts,
	List<MapPostMarkerResponse> posts
) {
}
