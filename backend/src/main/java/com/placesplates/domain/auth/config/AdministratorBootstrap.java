package com.placesplates.domain.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;

@Component
@ConditionalOnProperty(prefix = "places-plates.bootstrap-admin", name = "enabled", havingValue = "true")
public class AdministratorBootstrap implements ApplicationRunner {

	private final AdministratorAccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;
	private final String email;
	private final String password;

	public AdministratorBootstrap(
		AdministratorAccountRepository accountRepository,
		PasswordEncoder passwordEncoder,
		@Value("${places-plates.bootstrap-admin.email:}") String email,
		@Value("${places-plates.bootstrap-admin.password:}") String password
	) {
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || password.length() < 12) {
			throw new IllegalStateException("Administrator bootstrap credentials are required when enabled");
		}
		if (accountRepository.findByEmailIgnoreCase(email).isEmpty()) {
			accountRepository.save(AdministratorAccount.create(email, passwordEncoder.encode(password)));
		}
	}
}
