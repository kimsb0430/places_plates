package com.placesplates.domain.photo.dto;

public record UploadTicketResponse(
	String endpoint,
	String token,
	String bucketName,
	String objectName
) {
}
