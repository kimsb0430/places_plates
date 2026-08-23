package com.placesplates.global.error;

public record ApiErrorResponse(
	String code,
	String message
) {
}
