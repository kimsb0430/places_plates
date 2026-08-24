package com.placesplates.infra.storage;

public record SignedUploadTicket(
	String endpoint,
	String token,
	String bucketName,
	String objectName
) {
}
