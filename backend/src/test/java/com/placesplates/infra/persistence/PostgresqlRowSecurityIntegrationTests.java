package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "POSTGRES_RLS_TEST_ENABLED", matches = "true")
class PostgresqlRowSecurityIntegrationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configurePostgresql(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
		registry.add("spring.datasource.username", () -> System.getenv("POSTGRES_TEST_USERNAME"));
		registry.add("spring.datasource.password", () -> System.getenv("POSTGRES_TEST_PASSWORD"));
		registry.add(
			"spring.flyway.locations",
			() -> "classpath:db/migration/common,classpath:db/migration/postgresql"
		);
		registry.add("places-plates.security.database-row-security-enabled", () -> "false");
	}

	@Test
	void ownerAndPublicModesReturnOnlyAllowedPosts() {
		UUID ownerA = createAccount();
		UUID ownerB = createAccount();
		UUID ownerAPublicPost = createPost(ownerA, "PUBLIC", "PUBLISHED");
		UUID ownerAPrivatePost = createPost(ownerA, "PRIVATE", "DRAFT");
		UUID ownerBPublicPost = createPost(ownerB, "PUBLIC", "PUBLISHED");
		UUID ownerBPrivatePost = createPost(ownerB, "PRIVATE", "DRAFT");

		setDatabaseContext(ownerA, "OWNER");
		assertThat(findVisiblePostIds()).containsExactlyInAnyOrder(ownerAPublicPost, ownerAPrivatePost);

		setDatabaseContext(ownerB, "OWNER");
		assertThat(findVisiblePostIds()).containsExactlyInAnyOrder(ownerBPublicPost, ownerBPrivatePost);

		setDatabaseContext(null, "PUBLIC");
		assertThat(findVisiblePostIds()).containsExactlyInAnyOrder(ownerAPublicPost, ownerBPublicPost);

		setDatabaseContext(null, "NONE");
		assertThat(findVisiblePostIds()).isEmpty();
	}

	@Test
	void publicModeHidesPrivateMastersAndUploadState() {
		UUID ownerId = createAccount();
		UUID publicPostId = createPost(ownerId, "PUBLIC", "PUBLISHED");
		setDatabaseContext(ownerId, "OWNER");
		UUID photoId = UUID.randomUUID();
		UUID sanitizedMasterId = UUID.randomUUID();
		UUID publicDetailId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO photos (id, owner_user_id, post_id, processing_status) VALUES (?, ?, ?, 'READY')",
			photoId,
			ownerId,
			publicPostId
		);
		jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied
			) VALUES (?, ?, 'SANITIZED_MASTER', 'PRIVATE', ?, 'image/webp', 1600, 1200, 1000, TRUE, FALSE)
			""",
			sanitizedMasterId,
			photoId,
			"private/" + sanitizedMasterId + ".webp"
		);
		jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied, watermark_version
			) VALUES (?, ?, 'PUBLIC_DETAIL', 'PUBLIC', ?, 'image/webp', 1200, 900, 800, TRUE, TRUE, 'v1')
			""",
			publicDetailId,
			photoId,
			"public/" + publicDetailId + ".webp"
		);
		jdbcTemplate.update(
			"INSERT INTO upload_batches (id, owner_user_id, expires_at) VALUES (?, ?, ?)",
			UUID.randomUUID(),
			ownerId,
			OffsetDateTime.now().plusHours(1)
		);

		setDatabaseContext(null, "PUBLIC");
		List<UUID> visibleAssetIds = jdbcTemplate.queryForList(
			"SELECT id FROM photo_assets ORDER BY id",
			UUID.class
		);

		assertThat(visibleAssetIds).containsExactly(publicDetailId).doesNotContain(sanitizedMasterId);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM upload_batches", Integer.class)).isZero();
	}

	@Test
	void ownerCannotInsertRowsForAnotherAccount() {
		UUID ownerA = createAccount();
		UUID ownerB = createAccount();
		setDatabaseContext(ownerA, "OWNER");

		assertThatThrownBy(() -> jdbcTemplate.update(
			"INSERT INTO posts (id, owner_user_id, category, title) VALUES (?, ?, 'RESTAURANT', ?)",
			UUID.randomUUID(),
			ownerB,
			"forbidden post"
		)).isInstanceOf(DataAccessException.class);
	}

	@Test
	void runtimeRoleCannotBypassRlsAndReceivesOnlyApplicationObjectPrivileges() {
		assertThat(jdbcTemplate.queryForObject(
			"SELECT NOT rolsuper AND NOT rolbypassrls FROM pg_roles WHERE rolname = 'placesplates_app'",
			Boolean.class
		)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT has_table_privilege('placesplates_app', 'public.posts', 'SELECT')",
			Boolean.class
		)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT has_table_privilege('placesplates_app', 'public.posts', 'DELETE')",
			Boolean.class
		)).isTrue();
	}

	private UUID createAccount() {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO app_users (id, email, password_hash) VALUES (?, ?, ?)",
			userId,
			userId + "@example.test",
			"test-password-hash"
		);
		return userId;
	}

	private UUID createPost(UUID ownerId, String visibility, String status) {
		setDatabaseContext(ownerId, "OWNER");
		UUID postId = UUID.randomUUID();
		if ("PUBLISHED".equals(status)) {
			UUID placeId = UUID.randomUUID();
			jdbcTemplate.update(
				"INSERT INTO places (id, created_by_user_id, source, name) VALUES (?, ?, 'MANUAL', ?)",
				placeId,
				ownerId,
				"test place"
			);
			jdbcTemplate.update(
				"""
				INSERT INTO posts (
				    id, owner_user_id, place_id, category, title, visibility, status,
				    public_visit_year, public_visit_month, published_at
				) VALUES (?, ?, ?, 'RESTAURANT', ?, ?, ?, 2026, 8, ?)
				""",
				postId,
				ownerId,
				placeId,
				"published post",
				visibility,
				status,
				OffsetDateTime.now()
			);
		} else {
			jdbcTemplate.update(
				"""
				INSERT INTO posts (id, owner_user_id, category, title, visibility, status)
				VALUES (?, ?, 'RESTAURANT', ?, ?, ?)
				""",
				postId,
				ownerId,
				"private post",
				visibility,
				status
			);
		}
		return postId;
	}

	private void setDatabaseContext(UUID ownerId, String requestMode) {
		jdbcTemplate.queryForMap(
			"""
			SELECT
			    set_config('app.current_user_id', ?, TRUE) AS current_user_id,
			    set_config('app.request_mode', ?, TRUE) AS request_mode
			""",
			ownerId == null ? "" : ownerId.toString(),
			requestMode
		);
	}

	private List<UUID> findVisiblePostIds() {
		return jdbcTemplate.queryForList("SELECT id FROM posts ORDER BY id", UUID.class);
	}
}
