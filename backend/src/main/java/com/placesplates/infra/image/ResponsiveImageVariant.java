package com.placesplates.infra.image;

import com.placesplates.domain.photo.entity.PhotoAssetVariantType;

public record ResponsiveImageVariant(
	PhotoAssetVariantType type,
	byte[] bytes,
	String mimeType,
	int width,
	int height,
	String watermarkVersion,
	String watermarkPosition
) {
}
