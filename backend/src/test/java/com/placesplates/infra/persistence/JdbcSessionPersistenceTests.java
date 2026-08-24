package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.placesplates.domain.auth.service.AdministratorPrincipal;

@SpringBootTest
class JdbcSessionPersistenceTests {

	@Autowired
	private JdbcIndexedSessionRepository sessionRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void clearSessions() {
		jdbcTemplate.update("DELETE FROM spring_session");
	}

	@Test
	void authenticatedSessionCanBeLoadedByAnotherRepositoryInstance() {
		AdministratorPrincipal principal = new AdministratorPrincipal(
			UUID.randomUUID(),
			"session-admin@example.test",
			"test-password-hash",
			"ADMIN",
			true
		);
		SecurityContext securityContext = new SecurityContextImpl(
			UsernamePasswordAuthenticationToken.authenticated(
				principal,
				null,
				principal.getAuthorities()
			)
		);
		SessionRepository<Session> primaryRepository = asSessionRepository(sessionRepository);
		Session session = primaryRepository.createSession();
		session.setAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
			securityContext
		);
		primaryRepository.save(session);

		JdbcIndexedSessionRepository restartedRepository = new JdbcIndexedSessionRepository(
			jdbcTemplate,
			new TransactionTemplate(transactionManager)
		);
		Session restoredSession = asSessionRepository(restartedRepository).findById(session.getId());
		assertThat(restoredSession).isNotNull();
		SecurityContext restoredContext = restoredSession.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
		);

		assertThat(restoredContext.getAuthentication().getPrincipal())
			.isEqualTo(principal);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM spring_session WHERE session_id = ?",
			Integer.class,
			session.getId()
		)).isEqualTo(1);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private SessionRepository<Session> asSessionRepository(JdbcIndexedSessionRepository repository) {
		return (SessionRepository) repository;
	}
}
