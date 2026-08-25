package com.placesplates.domain.photo.dto;

import java.util.UUID;

public record ImageSanitizationResponse(
	UUID jobId,
	UUID uploadItemId,
	UUID photoId,
	String status,
	String failureCode,
	String message
) {
	public static ImageSanitizationResponse completed(UUID jobId, UUID uploadItemId, UUID photoId) {
		return new ImageSanitizationResponse(
			jobId,
			uploadItemId,
			photoId,
			"COMPLETED",
			null,
			"사진의 방향 보정과 개인정보 메타데이터 제거가 완료되었습니다."
		);
	}

	public static ImageSanitizationResponse failed(
		UUID jobId,
		UUID uploadItemId,
		String failureCode,
		String message
	) {
		return new ImageSanitizationResponse(
			jobId,
			uploadItemId,
			null,
			"FAILED",
			failureCode,
			message
		);
	}
}
