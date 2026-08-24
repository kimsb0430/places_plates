package com.placesplates.domain.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UploadFailureRequest(
	@NotBlank @Pattern(regexp = "[A-Z0-9_]{1,50}") String failureCode
) {
}
