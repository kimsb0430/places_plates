package com.placesplates.domain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.placesplates.domain.auth.dto.CsrfTokenResponse;
import com.placesplates.domain.auth.dto.LoginRequest;
import com.placesplates.domain.auth.dto.SessionResponse;
import com.placesplates.domain.auth.service.AdministratorPrincipal;
import com.placesplates.domain.auth.service.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

	private final AuthenticationService authenticationService;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AuthenticationController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@GetMapping("/csrf")
	public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
		return CsrfTokenResponse.from(csrfToken);
	}

	@PostMapping("/login")
	public SessionResponse login(
		@Valid @RequestBody LoginRequest loginRequest,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		return authenticationService.login(loginRequest, request, response);
	}

	@GetMapping("/session")
	public SessionResponse getSession(@AuthenticationPrincipal AdministratorPrincipal principal) {
		return SessionResponse.from(principal);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(
		Authentication authentication,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		logoutHandler.logout(request, response, authentication);
		SecurityContextHolder.clearContext();
	}
}
