package com.placesplates.domain.photo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DraftPhotoEditRequest(
	@NotNull
	@Size(max = 100)
	List<@NotNull @Valid DraftPhotoEditItemRequest> photos
) {
}
