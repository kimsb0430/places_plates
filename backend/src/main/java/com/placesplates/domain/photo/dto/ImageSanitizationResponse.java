package com.placesplates.domain.photo.dto;

import java.util.List;
import java.util.UUID;

import com.placesplates.domain.photo.entity.PhotoAsset;

public record ImageSanitizationResponse(
	UUID jobId,
	UUID uploadItemId,
	UUID photoId,
	String status,
	String failureCode,
	String message,
	List<ImageVariantResponse> variants
) {
	public static ImageSanitizationResponse completed(
		UUID jobId,
		UUID uploadItemId,
		UUID photoId,
		List<PhotoAsset> assets
	) {
		return new ImageSanitizationResponse(
			jobId,
			uploadItemId,
			photoId,
			"COMPLETED",
			null,
			"사진 정제와 화면별 반응형 이미지 생성이 완료되었습니다.",
			assets.stream()
				.filter(asset -> asset.getVariantType().isResponsiveVariant())
				.map(ImageVariantResponse::from)
				.toList()
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
			message,
			List.of()
		);
	}
}
