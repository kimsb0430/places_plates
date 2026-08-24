package com.placesplates.domain.photo.dto;

import jakarta.validation.constraints.Min;

public record UploadProgressRequest(
	@Min(0) long uploadedBytes
) {
}
