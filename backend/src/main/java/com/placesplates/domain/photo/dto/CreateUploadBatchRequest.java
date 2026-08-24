package com.placesplates.domain.photo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.placesplates.domain.post.entity.PostCategory;

public record CreateUploadBatchRequest(
	PostCategory category,
	@NotEmpty @Size(max = 100) List<@Valid UploadFileRequest> files
) {
}
