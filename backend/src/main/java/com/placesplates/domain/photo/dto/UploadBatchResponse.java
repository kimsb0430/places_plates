package com.placesplates.domain.photo.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.placesplates.domain.photo.entity.UploadBatch;

public record UploadBatchResponse(
	UUID id,
	UUID draftPostId,
	String status,
	OffsetDateTime expiresAt,
	List<UploadItemResponse> items
) {
	public static UploadBatchResponse from(UploadBatch batch, List<UploadItemResponse> items) {
		return new UploadBatchResponse(
			batch.getId(),
			batch.getPostId(),
			batch.getStatus().name(),
			batch.getExpiresAt(),
			List.copyOf(items)
		);
	}
}
