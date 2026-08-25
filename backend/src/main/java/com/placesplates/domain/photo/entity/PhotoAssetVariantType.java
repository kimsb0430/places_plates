package com.placesplates.domain.photo.entity;

public enum PhotoAssetVariantType {
	SANITIZED_MASTER,
	THUMBNAIL,
	MAP_CARD,
	PUBLIC_DETAIL;

	public boolean isResponsiveVariant() {
		return this != SANITIZED_MASTER;
	}
}
