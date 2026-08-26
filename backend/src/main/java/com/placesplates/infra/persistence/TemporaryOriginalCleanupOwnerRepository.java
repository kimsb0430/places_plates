package com.placesplates.infra.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TemporaryOriginalCleanupOwnerRepository {

	private final JdbcTemplate jdbcTemplate;

	public TemporaryOriginalCleanupOwnerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<UUID> findCandidateOwnerIds(int limit) {
		return jdbcTemplate.query(
			"SELECT owner_user_id FROM list_temporary_original_cleanup_owners(?)",
			(resultSet, rowNumber) -> resultSet.getObject("owner_user_id", UUID.class),
			limit
		);
	}
}
