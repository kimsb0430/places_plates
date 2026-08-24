package com.placesplates.domain.photo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UploadFileRequest(
	@NotBlank @Size(max = 255) String clientFileName,
	@NotBlank @Size(max = 100) String mimeType,
	@Min(1) @Max(31_457_280) long byteSize
) {
}
