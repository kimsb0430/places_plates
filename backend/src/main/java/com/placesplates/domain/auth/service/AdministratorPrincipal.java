package com.placesplates.domain.auth.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.placesplates.domain.auth.entity.AccountStatus;
import com.placesplates.domain.auth.entity.AdministratorAccount;

public record AdministratorPrincipal(
	UUID userId,
	String email,
	String passwordHash,
	String role,
	boolean isEnabled
) implements UserDetails, Serializable {

	private static final long serialVersionUID = 1L;

	public static AdministratorPrincipal from(AdministratorAccount account) {
		return new AdministratorPrincipal(
			account.getId(),
			account.getEmail(),
			account.getPasswordHash(),
			account.getRole().name(),
			account.getStatus() == AccountStatus.ACTIVE
		);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return isEnabled;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}
}
