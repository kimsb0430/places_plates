package com.placesplates.domain.photo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateUploadBatchRequest(
	@NotEmpty @Size(max = 100) List<@Valid UploadFileRequest> files
) {
}
