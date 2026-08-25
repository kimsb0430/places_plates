package com.placesplates.infra.image;

public class ImageSanitizationException extends RuntimeException {

	private final String failureCode;

	public ImageSanitizationException(String failureCode, String message) {
		super(message);
		this.failureCode = failureCode;
	}

	public ImageSanitizationException(String failureCode, String message, Throwable cause) {
		super(message, cause);
		this.failureCode = failureCode;
	}

	public String getFailureCode() {
		return failureCode;
	}
}
