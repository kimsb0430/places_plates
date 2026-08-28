package com.placesplates.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.placesplates.domain.photo.exception.ImageProcessingJobException;
import com.placesplates.domain.photo.exception.DraftPhotoException;
import com.placesplates.domain.photo.exception.PhotoUploadException;
import com.placesplates.domain.post.exception.DraftPostException;
import com.placesplates.domain.post.exception.PublicPostException;
import com.placesplates.domain.place.exception.PlaceException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthenticationException() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new ApiErrorResponse("AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해주세요."));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidationException() {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("COMMON_INVALID_INPUT", "입력값을 확인해주세요."));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatchException() {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("COMMON_INVALID_QUERY", "요청 조건을 확인해주세요."));
	}

	@ExceptionHandler(PhotoUploadException.class)
	public ResponseEntity<ApiErrorResponse> handlePhotoUploadException(PhotoUploadException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(DraftPhotoException.class)
	public ResponseEntity<ApiErrorResponse> handleDraftPhotoException(DraftPhotoException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(ImageProcessingJobException.class)
	public ResponseEntity<ApiErrorResponse> handleImageProcessingJobException(
		ImageProcessingJobException exception
	) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(DraftPostException.class)
	public ResponseEntity<ApiErrorResponse> handleDraftPostException(DraftPostException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(PublicPostException.class)
	public ResponseEntity<ApiErrorResponse> handlePublicPostException(PublicPostException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(PlaceException.class)
	public ResponseEntity<ApiErrorResponse> handlePlaceException(PlaceException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
	}
}
