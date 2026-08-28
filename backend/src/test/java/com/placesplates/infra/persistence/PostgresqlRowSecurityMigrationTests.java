package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

class PostgresqlRowSecurityMigrationTests {

	private static final Set<String> PROTECTED_TABLES = Set.of(
		"profiles",
		"trips",
		"places",
		"posts",
		"restaurant_details",
		"destination_details",
		"tags",
		"post_tags",
		"photos",
		"photo_assets",
		"upload_batches",
		"upload_items",
		"image_processing_jobs"
	);

	@Test
	void migrationForcesRowSecurityOnEveryPrivateDataTable() throws IOException {
		String migration = readMigration();

		for (String tableName : PROTECTED_TABLES) {
			assertThat(migration)
				.contains("ALTER TABLE " + tableName + " ENABLE ROW LEVEL SECURITY;")
				.contains("ALTER TABLE " + tableName + " FORCE ROW LEVEL SECURITY;");
		}
		assertThat(StringUtils.countOccurrencesOf(migration, "FORCE ROW LEVEL SECURITY"))
			.isEqualTo(PROTECTED_TABLES.size());
	}

	@Test
	void publicPoliciesRequireExplicitPublishedVisibility() throws IOException {
		String migration = readMigration();

		assertThat(migration)
			.contains("app_request_mode() = 'PUBLIC'")
			.contains("visibility = 'PUBLIC'")
			.contains("status = 'PUBLISHED'")
			.contains("access_level = 'PUBLIC'")
			.contains("metadata_scan_passed = TRUE")
			.contains("watermark_applied = TRUE");
	}

	@Test
	void uploadPoliciesDoNotDefinePublicAccess() throws IOException {
		String migration = readMigration();

		assertThat(migration)
			.contains("CREATE POLICY upload_batches_owner_all")
			.contains("CREATE POLICY upload_items_owner_all")
			.contains("CREATE POLICY image_processing_jobs_owner_all")
			.contains("upload_batches.post_id = image_processing_jobs.post_id")
			.doesNotContain("upload_batches_public")
			.doesNotContain("upload_items_public")
			.doesNotContain("image_processing_jobs_public");
	}

	@Test
	void publicPlacePolicyRequiresALinkedPublishedPublicPost() throws IOException {
		String migration = readMigration("db/migration/postgresql/V15__allow_public_linked_place_read.sql");

		assertThat(migration)
			.contains("CREATE POLICY places_public_select ON places")
			.contains("app_request_mode() = 'PUBLIC'")
			.contains("posts.place_id = places.id")
			.contains("posts.visibility = 'PUBLIC'")
			.contains("posts.status = 'PUBLISHED'");
	}

	private String readMigration() throws IOException {
		return readMigration("db/migration/postgresql/V4__enforce_owner_scoped_row_security.sql")
			+ readMigration("db/migration/postgresql/V8__secure_image_processing_jobs.sql");
	}

	private String readMigration(String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
	}
}
