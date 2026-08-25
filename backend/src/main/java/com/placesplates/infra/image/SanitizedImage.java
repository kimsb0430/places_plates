package com.placesplates.infra.image;

public record SanitizedImage(
	byte[] bytes,
	String mimeType,
	int width,
	int height
) {
}
