package com.placesplates.domain.auth.dto;

import java.util.UUID;

import com.placesplates.domain.auth.service.AdministratorPrincipal;

public record SessionResponse(
	UUID userId,
	String email,
	String role
) {

	public static SessionResponse from(AdministratorPrincipal principal) {
		return new SessionResponse(principal.userId(), principal.email(), principal.role());
	}
}
