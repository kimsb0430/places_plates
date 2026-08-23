package com.placesplates.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
