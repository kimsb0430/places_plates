package com.placesplates.domain.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import com.placesplates.domain.auth.dto.LoginRequest;
import com.placesplates.domain.auth.dto.SessionResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class AuthenticationService {

	private final AuthenticationManager authenticationManager;
	private final HttpSessionSecurityContextRepository securityContextRepository =
		new HttpSessionSecurityContextRepository();

	public AuthenticationService(AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	public SessionResponse login(
		LoginRequest loginRequest,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		Authentication authentication = authenticationManager.authenticate(
			UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password())
		);
		HttpSession existingSession = request.getSession(false);
		if (existingSession != null) {
			request.changeSessionId();
		}
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		securityContext.setAuthentication(authentication);
		SecurityContextHolder.setContext(securityContext);
		securityContextRepository.saveContext(securityContext, request, response);
		return SessionResponse.from((AdministratorPrincipal) authentication.getPrincipal());
	}
}
