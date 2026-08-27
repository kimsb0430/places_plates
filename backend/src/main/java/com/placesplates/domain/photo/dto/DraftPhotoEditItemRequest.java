package com.placesplates.domain.photo.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DraftPhotoEditItemRequest(
	@NotNull UUID photoId,
	boolean cover,
	@Size(max = 500) String altText
) {
}
