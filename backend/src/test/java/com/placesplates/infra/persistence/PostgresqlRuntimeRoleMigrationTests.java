package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class PostgresqlRuntimeRoleMigrationTests {

	@Test
	void migrationRequiresRestrictedRuntimeRoleAndGrantsOnlyApplicationPrivileges() throws IOException {
		String migration = readMigration();

		assertThat(migration)
			.contains("Required runtime role placesplates_app does not exist")
			.contains("GRANT USAGE ON SCHEMA public TO placesplates_app;")
			.contains("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO placesplates_app;")
			.contains("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO placesplates_app;")
			.contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE image_processing_jobs TO placesplates_app;")
			.doesNotContain("BYPASSRLS")
			.doesNotContain("SUPERUSER");
	}

	@Test
	void migrationRemovesSupabaseDataApiPrivilegesFromBackendOwnedSchema() throws IOException {
		String migration = readMigration();

		assertThat(migration)
			.contains("ARRAY['anon', 'authenticated']")
			.contains("REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public")
			.contains("REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public")
			.contains("REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public")
			.contains("REVOKE ALL PRIVILEGES ON SCHEMA public")
			.contains("REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;");
		assertThat(migration).contains(
			"REVOKE ALL PRIVILEGES ON TABLE image_processing_jobs FROM %I"
		);
	}

	private String readMigration() throws IOException {
		return readMigration("db/migration/postgresql/V5__grant_runtime_role_and_restrict_data_api.sql")
			+ readMigration("db/migration/postgresql/V8__secure_image_processing_jobs.sql");
	}

	private String readMigration(String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
	}
}
