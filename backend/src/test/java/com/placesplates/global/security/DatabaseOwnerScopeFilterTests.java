package com.placesplates.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.placesplates.domain.auth.service.AdministratorPrincipal;

import jakarta.servlet.FilterChain;

class DatabaseOwnerScopeFilterTests {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void publicApiUsesPublicDatabaseMode() {
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/public/posts")).isTrue();
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/public/posts"))
			.isEqualTo(DatabaseAccessMode.PUBLIC);
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/public"))
			.isEqualTo(DatabaseAccessMode.PUBLIC);
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/publicity"))
			.isEqualTo(DatabaseAccessMode.OWNER);
	}

	@Test
	void protectedApiUsesOwnerDatabaseMode() {
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/manage/posts")).isTrue();
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/manage/posts"))
			.isEqualTo(DatabaseAccessMode.OWNER);
	}

	@Test
	void authenticationAndHealthApisDoNotOpenDatabaseScope() {
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/auth/session")).isFalse();
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/health")).isFalse();
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/other/path")).isFalse();
	}

	@Test
	void ownerScopeUsesAuthenticatedPrincipalAndIgnoresSpoofedOwnerHeader() throws Exception {
		UUID authenticatedUserId = UUID.randomUUID();
		UUID spoofedUserId = UUID.randomUUID();
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		DatabaseOwnerScopeFilter filter = createFilter(jdbcTemplate);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/manage/posts");
		request.setRequestURI("/api/v1/manage/posts");
		request.addHeader("X-Owner-User-Id", spoofedUserId.toString());
		setAuthentication(new AdministratorPrincipal(
			authenticatedUserId,
			"administrator@example.test",
			"password-hash",
			"ADMIN",
			true
		));

		filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

		verify(jdbcTemplate).queryForMap(
			anyString(),
			eq(authenticatedUserId.toString()),
			eq(DatabaseAccessMode.OWNER.name())
		);
		verify(jdbcTemplate, never()).queryForMap(anyString(), eq(spoofedUserId.toString()), anyString());
	}

	@Test
	void publicScopeNeverUsesAuthenticatedOwnerIdentity() throws Exception {
		UUID authenticatedUserId = UUID.randomUUID();
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		DatabaseOwnerScopeFilter filter = createFilter(jdbcTemplate);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/posts");
		request.setRequestURI("/api/v1/public/posts");
		setAuthentication(new AdministratorPrincipal(
			authenticatedUserId,
			"administrator@example.test",
			"password-hash",
			"ADMIN",
			true
		));

		filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

		verify(jdbcTemplate).queryForMap(
			anyString(),
			eq(""),
			eq(DatabaseAccessMode.PUBLIC.name())
		);
	}

	@Test
	void frameworkRoleWithoutAdministratorPrincipalCannotOpenOwnerScope() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		TransactionTemplate transactionTemplate = synchronousTransactionTemplate();
		DatabaseOwnerScopeFilter filter = new DatabaseOwnerScopeFilter(transactionTemplate, jdbcTemplate, true);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/manage/posts");
		request.setRequestURI("/api/v1/manage/posts");
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated("forged-user", "password", java.util.List.of())
		);

		assertThatThrownBy(() -> filter.doFilter(
			request,
			new MockHttpServletResponse(),
			mock(FilterChain.class)
		)).isInstanceOf(AccessDeniedException.class);
		verify(transactionTemplate, never()).executeWithoutResult(any());
		verify(jdbcTemplate, never()).queryForMap(anyString(), any(Object[].class));
	}

	private DatabaseOwnerScopeFilter createFilter(JdbcTemplate jdbcTemplate) {
		return new DatabaseOwnerScopeFilter(synchronousTransactionTemplate(), jdbcTemplate, true);
	}

	@SuppressWarnings("unchecked")
	private TransactionTemplate synchronousTransactionTemplate() {
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		org.mockito.Mockito.doAnswer(invocation -> {
			Consumer<TransactionStatus> action = invocation.getArgument(0);
			action.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactionTemplate).executeWithoutResult(any(Consumer.class));
		return transactionTemplate;
	}

	private void setAuthentication(AdministratorPrincipal principal) {
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, principal.passwordHash(), principal.getAuthorities())
		);
	}
}
