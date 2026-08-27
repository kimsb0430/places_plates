package com.placesplates.domain.photo.dto;

import java.util.UUID;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;

public record DraftPhotoResponse(
	UUID id,
	int displayOrder,
	boolean cover,
	String altText,
	PhotoProcessingStatus processingStatus,
	String thumbnailPath
) {

	public static DraftPhotoResponse from(Photo photo, UUID draftId, boolean thumbnailAvailable) {
		String thumbnailPath = thumbnailAvailable
			? "/api/v1/manage/drafts/" + draftId + "/photos/" + photo.getId() + "/thumbnail"
			: null;
		return new DraftPhotoResponse(
			photo.getId(),
			photo.getDisplayOrder(),
			photo.isCover(),
			photo.getAltText(),
			photo.getProcessingStatus(),
			thumbnailPath
		);
	}
}
