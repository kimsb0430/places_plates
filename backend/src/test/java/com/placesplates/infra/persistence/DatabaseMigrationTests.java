package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseMigrationTests {

	private static final Set<String> REQUIRED_TABLES = Set.of(
		"APP_USERS",
		"PROFILES",
		"TRIPS",
		"PLACES",
		"POSTS",
		"RESTAURANT_DETAILS",
		"DESTINATION_DETAILS",
		"TAGS",
		"POST_TAGS",
		"UPLOAD_BATCHES",
		"UPLOAD_ITEMS",
		"PHOTOS",
		"PHOTO_ASSETS"
	);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesRequiredTables() {
		Set<String> actualTables = Set.copyOf(jdbcTemplate.queryForList(
			"SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'",
			String.class
		));

		assertThat(actualTables).containsAll(REQUIRED_TABLES);
	}

	@Test
	void postCategoryRejectsUnknownValues() {
		UUID userId = createUser();

		assertThatThrownBy(() -> jdbcTemplate.update(
			"INSERT INTO posts (id, owner_user_id, category, title) VALUES (?, ?, ?, ?)",
			UUID.randomUUID(),
			userId,
			"UNKNOWN",
			"invalid category"
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void publicPhotoAssetRequiresMetadataScanAndWatermark() {
		UUID userId = createUser();
		UUID photoId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO photos (id, owner_user_id, processing_status) VALUES (?, ?, ?)",
			photoId,
			userId,
			"READY"
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			photoId,
			"PUBLIC_DETAIL",
			"PUBLIC",
			"public/unsafe.webp",
			"image/webp",
			1200,
			800,
			100_000,
			true,
			false
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void completedUploadCannotRetainTemporaryOriginalPath() {
		UUID userId = createUser();
		UUID batchId = UUID.randomUUID();
		UUID photoId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO upload_batches (id, owner_user_id, expires_at) VALUES (?, ?, ?)",
			batchId,
			userId,
			OffsetDateTime.now().plusHours(1)
		);
		jdbcTemplate.update(
			"INSERT INTO photos (id, owner_user_id, processing_status) VALUES (?, ?, ?)",
			photoId,
			userId,
			"READY"
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO upload_items (
			    id, upload_batch_id, result_photo_id, temporary_storage_key,
			    processing_status, expires_at, original_deleted_at
			) VALUES (?, ?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			batchId,
			photoId,
			"temporary/raw-upload.jpg",
			"COMPLETED",
			OffsetDateTime.now().plusHours(1),
			OffsetDateTime.now()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID createUser() {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO app_users (id, email, password_hash) VALUES (?, ?, ?)",
			userId,
			userId + "@example.test",
			"test-password-hash"
		);
		return userId;
	}
}
