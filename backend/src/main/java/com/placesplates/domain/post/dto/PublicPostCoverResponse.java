package com.placesplates.domain.post.dto;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.post.entity.DraftPost;

public record PublicPostCoverResponse(
	String path,
	String altText,
	int width,
	int height
) {

	public static PublicPostCoverResponse from(DraftPost post, Photo photo, PhotoAsset asset) {
		String altText = photo.getAltText() == null || photo.getAltText().isBlank()
			? post.getTitle() + " 대표 사진"
			: photo.getAltText();
		return new PublicPostCoverResponse(
			"/api/v1/public/posts/" + post.getId() + "/cover",
			altText,
			asset.getWidth(),
			asset.getHeight()
		);
	}
}
