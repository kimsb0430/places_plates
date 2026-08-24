package com.placesplates.domain.photo.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.placesplates.domain.photo.entity.UploadItem;

public record UploadItemResponse(
	UUID id,
	String clientFileName,
	String mimeType,
	long byteSize,
	long uploadedBytes,
	String status,
	int attemptCount,
	String failureCode,
	OffsetDateTime expiresAt,
	UploadTicketResponse uploadTicket
) {
	public static UploadItemResponse from(UploadItem item, UploadTicketResponse uploadTicket) {
		return new UploadItemResponse(
			item.getId(),
			item.getClientFileLabel(),
			item.getMimeType(),
			item.getByteSize(),
			item.getUploadedBytes(),
			item.getStatus().name(),
			item.getAttemptCount(),
			item.getFailureCode(),
			item.getExpiresAt(),
			uploadTicket
		);
	}
}
