package com.placesplates.domain.post.dto;

import java.util.UUID;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.post.entity.DraftPost;

public record PublicPostPhotoResponse(
	UUID id,
	String path,
	String altText,
	int width,
	int height,
	boolean cover
) {

	public static PublicPostPhotoResponse from(DraftPost post, Photo photo, PhotoAsset asset) {
		String altText = photo.getAltText() == null || photo.getAltText().isBlank()
			? post.getTitle() + " 사진 " + (photo.getDisplayOrder() + 1)
			: photo.getAltText();
		return new PublicPostPhotoResponse(
			photo.getId(),
			"/api/v1/public/posts/" + post.getId() + "/photos/" + photo.getId(),
			altText,
			asset.getWidth(),
			asset.getHeight(),
			photo.isCover()
		);
	}
}
