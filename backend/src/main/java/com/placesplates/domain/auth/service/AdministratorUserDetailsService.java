package com.placesplates.domain.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.auth.repository.AdministratorAccountRepository;

@Service
@Transactional(readOnly = true)
public class AdministratorUserDetailsService implements UserDetailsService {

	private final AdministratorAccountRepository accountRepository;

	public AdministratorUserDetailsService(AdministratorAccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return accountRepository.findByEmailIgnoreCase(email)
			.map(AdministratorPrincipal::from)
			.filter(principal -> "ADMIN".equals(principal.role()))
			.orElseThrow(() -> new UsernameNotFoundException("Administrator account was not found"));
	}
}
