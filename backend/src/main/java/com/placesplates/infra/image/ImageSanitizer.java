package com.placesplates.infra.image;

public interface ImageSanitizer {

	SanitizedImage sanitize(byte[] source, String declaredMimeType);
}
