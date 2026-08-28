package com.placesplates.domain.post.dto;

import java.util.List;

public record PublicPlaceHistoryResponse(
	PublicPostPlaceResponse place,
	int visitCount,
	List<PublicPlaceVisitResponse> visits
) {
}
