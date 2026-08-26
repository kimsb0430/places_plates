package com.placesplates.infra.image;

public interface StoredImageVerifier {

	void verify(byte[] bytes, String mimeType, int expectedWidth, int expectedHeight, long expectedByteSize);
}
