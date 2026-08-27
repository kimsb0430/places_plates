package com.placesplates.domain.post.dto;

import java.util.List;

public record PostPublicationReadinessResponse(
	boolean ready,
	List<PostPublicationCheckResponse> checks
) {
}
