package com.placesplates.domain.post.dto;

public record PublicPostPhotoContent(
	byte[] bytes,
	String mimeType
) {
}
