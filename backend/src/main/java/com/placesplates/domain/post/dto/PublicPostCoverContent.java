package com.placesplates.domain.post.dto;

public record PublicPostCoverContent(
	byte[] bytes,
	String mimeType
) {
}
