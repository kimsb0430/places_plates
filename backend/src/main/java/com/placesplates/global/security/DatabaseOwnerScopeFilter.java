package com.placesplates.global.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import com.placesplates.domain.auth.service.AdministratorPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DatabaseOwnerScopeFilter extends OncePerRequestFilter {

	private static final String API_ROOT = "/api/v1";
	private static final String AUTH_ROOT = "/api/v1/auth";
	private static final String PUBLIC_ROOT = "/api/v1/public";
	private static final String HEALTH_PATH = "/api/v1/health";

	private final TransactionTemplate transactionTemplate;
	private final JdbcTemplate jdbcTemplate;
	private final boolean isRowSecurityEnabled;

	public DatabaseOwnerScopeFilter(
		TransactionTemplate transactionTemplate,
		JdbcTemplate jdbcTemplate,
		boolean isRowSecurityEnabled
	) {
		this.transactionTemplate = transactionTemplate;
		this.jdbcTemplate = jdbcTemplate;
		this.isRowSecurityEnabled = isRowSecurityEnabled;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !isRowSecurityEnabled || !requiresDatabaseScope(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		DatabaseAccessMode accessMode = resolveAccessMode(request.getRequestURI());
		UUID currentUserId = accessMode == DatabaseAccessMode.PUBLIC ? null : getAuthenticatedUserId();

		try {
			transactionTemplate.executeWithoutResult(status -> {
				setDatabaseContext(currentUserId, accessMode);
				try {
					filterChain.doFilter(request, response);
				} catch (IOException | ServletException exception) {
					throw new FilterChainException(exception);
				} finally {
					if (response.getStatus() >= HttpServletResponse.SC_BAD_REQUEST) {
						status.setRollbackOnly();
					}
				}
			});
		} catch (FilterChainException exception) {
			if (exception.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			if (exception.getCause() instanceof ServletException servletException) {
				throw servletException;
			}
			throw exception;
		}
	}

	static boolean requiresDatabaseScope(String requestUri) {
		return isWithinPath(requestUri, API_ROOT)
			&& !isWithinPath(requestUri, AUTH_ROOT)
			&& !HEALTH_PATH.equals(requestUri);
	}

	static DatabaseAccessMode resolveAccessMode(String requestUri) {
		return isWithinPath(requestUri, PUBLIC_ROOT)
			? DatabaseAccessMode.PUBLIC
			: DatabaseAccessMode.OWNER;
	}

	private static boolean isWithinPath(String requestUri, String rootPath) {
		return requestUri.equals(rootPath) || requestUri.startsWith(rootPath + "/");
	}

	private UUID getAuthenticatedUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AdministratorPrincipal principal)) {
			throw new AccessDeniedException("Authenticated owner context is required");
		}
		return principal.userId();
	}

	private void setDatabaseContext(UUID currentUserId, DatabaseAccessMode accessMode) {
		jdbcTemplate.queryForMap(
			"""
			SELECT
			    set_config('app.current_user_id', ?, TRUE) AS current_user_id,
			    set_config('app.request_mode', ?, TRUE) AS request_mode
			""",
			currentUserId == null ? "" : currentUserId.toString(),
			accessMode.name()
		);
	}

	private static class FilterChainException extends RuntimeException {

		FilterChainException(Exception cause) {
			super(cause);
		}
	}
}
