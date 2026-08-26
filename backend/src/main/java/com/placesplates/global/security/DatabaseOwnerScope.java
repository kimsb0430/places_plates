package com.placesplates.global.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseOwnerScope {

	private final JdbcTemplate jdbcTemplate;
	private final boolean isRowSecurityEnabled;

	public DatabaseOwnerScope(
		JdbcTemplate jdbcTemplate,
		@Value("${places-plates.security.database-row-security-enabled:true}") boolean isRowSecurityEnabled
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.isRowSecurityEnabled = isRowSecurityEnabled;
	}

	public void activateOwner(UUID ownerUserId) {
		if (!isRowSecurityEnabled) {
			return;
		}
		jdbcTemplate.queryForMap(
			"""
			SELECT
			    set_config('app.current_user_id', ?, TRUE) AS current_user_id,
			    set_config('app.request_mode', 'OWNER', TRUE) AS request_mode
			""",
			ownerUserId.toString()
		);
	}
}
