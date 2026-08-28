package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
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
		"PHOTO_ASSETS",
		"IMAGE_PROCESSING_JOBS",
		"SPRING_SESSION",
		"SPRING_SESSION_ATTRIBUTES"
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
	void accountRoleDefaultsToMember() {
		UUID userId = createUser();

		assertThat(jdbcTemplate.queryForObject(
			"SELECT role FROM app_users WHERE id = ?",
			String.class,
			userId
		)).isEqualTo("MEMBER");
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
	void watermarkedAssetRequiresVersionAndSupportedPosition() {
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
			    width, height, byte_size, metadata_scan_passed, watermark_applied,
			    watermark_version, watermark_position
			) VALUES (?, ?, 'PUBLIC_DETAIL', 'PUBLIC', ?, 'image/jpeg', 1200, 800, 100000, TRUE, TRUE, NULL, NULL)
			""",
			UUID.randomUUID(),
			photoId,
			"variants/unsafe-watermark.jpg"
		)).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied,
			    watermark_version, watermark_position
			) VALUES (?, ?, 'PUBLIC_DETAIL', 'PUBLIC', ?, 'image/jpeg', 1200, 800, 100000, TRUE, TRUE, ?, ?)
			""",
			UUID.randomUUID(),
			photoId,
			"variants/safe-watermark.jpg",
			"places-plates-corner-v1",
			"BOTTOM_RIGHT"
		)).isOne();
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

	@Test
	void expiredUploadRequiresDeletedTemporaryOriginal() {
		UUID userId = createUser();
		UUID batchId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO upload_batches (id, owner_user_id, expires_at) VALUES (?, ?, ?)",
			batchId,
			userId,
			OffsetDateTime.now().minusHours(1)
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO upload_items (
			    id, upload_batch_id, temporary_storage_key, processing_status, expires_at
			) VALUES (?, ?, ?, 'EXPIRED', ?)
			""",
			UUID.randomUUID(),
			batchId,
			"temporary/expired.jpg",
			OffsetDateTime.now().minusHours(1)
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void uploadProgressCannotExceedDeclaredFileSize() {
		UUID userId = createUser();
		UUID batchId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO upload_batches (id, owner_user_id, expires_at) VALUES (?, ?, ?)",
			batchId,
			userId,
			OffsetDateTime.now().plusHours(1)
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO upload_items (
			    id, upload_batch_id, processing_status, expires_at, byte_size, uploaded_bytes
			) VALUES (?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			batchId,
			"UPLOADING",
			OffsetDateTime.now().plusHours(1),
			1_024,
			2_048
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void uploadItemCanHaveOnlyOneImageProcessingJob() {
		UUID userId = createUser();
		UUID postId = UUID.randomUUID();
		UUID batchId = UUID.randomUUID();
		UUID uploadItemId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO posts (id, owner_user_id, category, title) VALUES (?, ?, 'RESTAURANT', ?)",
			postId,
			userId,
			"processing draft"
		);
		jdbcTemplate.update(
			"INSERT INTO upload_batches (id, owner_user_id, post_id, expires_at) VALUES (?, ?, ?, ?)",
			batchId,
			userId,
			postId,
			OffsetDateTime.now().plusHours(1)
		);
		jdbcTemplate.update(
			"INSERT INTO upload_items (id, upload_batch_id, expires_at) VALUES (?, ?, ?)",
			uploadItemId,
			batchId,
			OffsetDateTime.now().plusHours(1)
		);
		jdbcTemplate.update(
			"""
			INSERT INTO image_processing_jobs (id, owner_user_id, post_id, upload_item_id)
			VALUES (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			postId,
			uploadItemId
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO image_processing_jobs (id, owner_user_id, post_id, upload_item_id)
			VALUES (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			postId,
			uploadItemId
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingJdbcSessionAlsoDeletesItsAttributes() {
		String primaryId = UUID.randomUUID().toString();
		jdbcTemplate.update(
			"""
			INSERT INTO spring_session (
			    primary_id, session_id, creation_time, last_access_time,
			    max_inactive_interval, expiry_time
			) VALUES (?, ?, ?, ?, ?, ?)
			""",
			primaryId,
			UUID.randomUUID().toString(),
			1L,
			1L,
			1_800,
			1_801_000L
		);
		jdbcTemplate.update(
			"""
			INSERT INTO spring_session_attributes (session_primary_id, attribute_name, attribute_bytes)
			VALUES (?, ?, ?)
			""",
			primaryId,
			"test-attribute",
			new byte[] {1}
		);

		jdbcTemplate.update("DELETE FROM spring_session WHERE primary_id = ?", primaryId);

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM spring_session_attributes WHERE session_primary_id = ?",
			Integer.class,
			primaryId
		)).isZero();
	}

	@Test
	void readyBackfillRequiresCompletedMetadataFreeSanitizedMaster() throws IOException {
		UUID userId = createUser();
		UUID postId = UUID.randomUUID();
		UUID batchId = UUID.randomUUID();
		UUID readyPhotoId = UUID.randomUUID();
		UUID unsafePhotoId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO posts (id, owner_user_id, category, title) VALUES (?, ?, 'DESTINATION', ?)",
			postId,
			userId,
			"backfill draft"
		);
		jdbcTemplate.update(
			"INSERT INTO upload_batches (id, owner_user_id, post_id, expires_at) VALUES (?, ?, ?, ?)",
			batchId,
			userId,
			postId,
			OffsetDateTime.now().plusHours(1)
		);
		insertProcessingPhoto(readyPhotoId, userId, postId);
		insertProcessingPhoto(unsafePhotoId, userId, postId);
		insertCompletedSanitizedUpload(batchId, readyPhotoId, userId, postId, true);
		insertCompletedSanitizedUpload(batchId, unsafePhotoId, userId, postId, false);

		ClassPathResource migration = new ClassPathResource(
			"db/migration/common/V11__backfill_ready_sanitized_photos.sql"
		);
		jdbcTemplate.execute(migration.getContentAsString(StandardCharsets.UTF_8));

		assertThat(photoStatus(readyPhotoId)).isEqualTo("READY");
		assertThat(photoStatus(unsafePhotoId)).isEqualTo("PROCESSING");
	}

	@Test
	void coordinateBackfillEnablesOnlyPostsConnectedToCoordinatePlaces() throws IOException {
		UUID userId = createUser();
		UUID coordinatePlaceId = UUID.randomUUID();
		UUID addressOnlyPlaceId = UUID.randomUUID();
		UUID mappablePostId = UUID.randomUUID();
		UUID hiddenPostId = UUID.randomUUID();
		jdbcTemplate.update(
			"""
			INSERT INTO places (id, created_by_user_id, source, name, latitude, longitude)
			VALUES (?, ?, 'MANUAL', 'coordinate place', 37.566500, 126.978000)
			""",
			coordinatePlaceId,
			userId
		);
		jdbcTemplate.update(
			"""
			INSERT INTO places (id, created_by_user_id, source, name)
			VALUES (?, ?, 'MANUAL', 'address only place')
			""",
			addressOnlyPlaceId,
			userId
		);
		jdbcTemplate.update(
			"INSERT INTO posts (id, owner_user_id, place_id, category, title) VALUES (?, ?, ?, 'RESTAURANT', 'mappable')",
			mappablePostId,
			userId,
			coordinatePlaceId
		);
		jdbcTemplate.update(
			"INSERT INTO posts (id, owner_user_id, place_id, category, title) VALUES (?, ?, ?, 'DESTINATION', 'hidden')",
			hiddenPostId,
			userId,
			addressOnlyPlaceId
		);

		ClassPathResource migration = new ClassPathResource(
			"db/migration/common/V16__enable_map_coordinates_for_connected_places.sql"
		);
		jdbcTemplate.execute(migration.getContentAsString(StandardCharsets.UTF_8));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT coordinate_visibility FROM posts WHERE id = ?",
			String.class,
			mappablePostId
		)).isEqualTo("EXACT");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT coordinate_visibility FROM posts WHERE id = ?",
			String.class,
			hiddenPostId
		)).isEqualTo("HIDDEN");
	}

	private void insertProcessingPhoto(UUID photoId, UUID userId, UUID postId) {
		jdbcTemplate.update(
			"INSERT INTO photos (id, owner_user_id, post_id, processing_status) VALUES (?, ?, ?, 'PROCESSING')",
			photoId,
			userId,
			postId
		);
	}

	private void insertCompletedSanitizedUpload(
		UUID batchId,
		UUID photoId,
		UUID userId,
		UUID postId,
		boolean metadataScanPassed
	) {
		UUID uploadItemId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO upload_items (id, upload_batch_id, result_photo_id, expires_at) VALUES (?, ?, ?, ?)",
			uploadItemId,
			batchId,
			photoId,
			OffsetDateTime.now().plusHours(1)
		);
		jdbcTemplate.update(
			"""
			INSERT INTO image_processing_jobs (
			    id, owner_user_id, post_id, upload_item_id, status, completed_at
			) VALUES (?, ?, ?, ?, 'COMPLETED', ?)
			""",
			UUID.randomUUID(),
			userId,
			postId,
			uploadItemId,
			OffsetDateTime.now()
		);
		jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed
			) VALUES (?, ?, 'SANITIZED_MASTER', 'PRIVATE', ?, 'image/jpeg', 800, 500, 1024, ?)
			""",
			UUID.randomUUID(),
			photoId,
			"sanitized/" + photoId + ".jpg",
			metadataScanPassed
		);
	}

	private String photoStatus(UUID photoId) {
		return jdbcTemplate.queryForObject(
			"SELECT processing_status FROM photos WHERE id = ?",
			String.class,
			photoId
		);
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
