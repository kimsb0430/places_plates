package com.placesplates.domain.post.dto;

public record PostPublicationCheckResponse(
	String code,
	String label,
	boolean passed
) {
}
