package com.placesplates.domain.photo.dto;

import com.placesplates.domain.photo.entity.PhotoAsset;

public record ImageVariantResponse(
	String type,
	int width,
	int height,
	long byteSize
) {
	public static ImageVariantResponse from(PhotoAsset asset) {
		return new ImageVariantResponse(
			asset.getVariantType().name(),
			asset.getWidth(),
			asset.getHeight(),
			asset.getByteSize()
		);
	}
}
