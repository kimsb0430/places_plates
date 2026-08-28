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
		UUID ownerAPublicDraft = createPost(ownerA, "PUBLIC", "DRAFT");
		UUID ownerAUnlistedPost = createPost(ownerA, "UNLISTED", "PUBLISHED");
		UUID ownerAPrivatePost = createPost(ownerA, "PRIVATE", "PUBLISHED");
		UUID ownerBPublicPost = createPost(ownerB, "PUBLIC", "PUBLISHED");
		UUID ownerBPrivatePost = createPost(ownerB, "PRIVATE", "DRAFT");

		setDatabaseContext(ownerA, "OWNER");
		assertThat(findVisiblePostIds()).containsExactlyInAnyOrder(
			ownerAPublicPost,
			ownerAPublicDraft,
			ownerAUnlistedPost,
			ownerAPrivatePost
		);

		setDatabaseContext(ownerB, "OWNER");
		assertThat(findVisiblePostIds()).containsExactlyInAnyOrder(ownerBPublicPost, ownerBPrivatePost);

		setDatabaseContext(null, "PUBLIC");
		assertThat(findVisiblePostIds()).containsExactlyInAnyOrder(ownerAPublicPost, ownerBPublicPost);

		setDatabaseContext(null, "NONE");
		assertThat(findVisiblePostIds()).isEmpty();
	}

	@Test
	void publicModeHidesDetailsPhotosAndAssetsForEveryNonPublicPostState() {
		UUID ownerId = createAccount();
		UUID publicPostId = createPost(ownerId, "PUBLIC", "PUBLISHED");
		UUID publicDraftId = createPost(ownerId, "PUBLIC", "DRAFT");
		UUID unlistedPostId = createPost(ownerId, "UNLISTED", "PUBLISHED");
		UUID privatePostId = createPost(ownerId, "PRIVATE", "PUBLISHED");
		PublicPhotoGraph publicGraph = insertReadyPublicPhoto(ownerId, publicPostId);
		PublicPhotoGraph publicDraftGraph = insertReadyPublicPhoto(ownerId, publicDraftId);
		PublicPhotoGraph unlistedGraph = insertReadyPublicPhoto(ownerId, unlistedPostId);
		PublicPhotoGraph privateGraph = insertReadyPublicPhoto(ownerId, privatePostId);
		insertRestaurantDetails(publicPostId, publicDraftId, unlistedPostId, privatePostId);

		setDatabaseContext(null, "PUBLIC");
		List<UUID> visibleDetailPostIds = jdbcTemplate.queryForList(
			"SELECT post_id FROM restaurant_details ORDER BY post_id",
			UUID.class
		);
		List<UUID> visiblePhotoIds = jdbcTemplate.queryForList("SELECT id FROM photos ORDER BY id", UUID.class);
		List<UUID> visibleAssetIds = jdbcTemplate.queryForList(
			"SELECT id FROM photo_assets ORDER BY id",
			UUID.class
		);

		assertThat(visibleDetailPostIds)
			.containsExactly(publicPostId)
			.doesNotContain(publicDraftId, unlistedPostId, privatePostId);
		assertThat(visiblePhotoIds)
			.containsExactly(publicGraph.photoId())
			.doesNotContain(publicDraftGraph.photoId(), unlistedGraph.photoId(), privateGraph.photoId());
		assertThat(visibleAssetIds)
			.containsExactly(publicGraph.assetId())
			.doesNotContain(publicDraftGraph.assetId(), unlistedGraph.assetId(), privateGraph.assetId());
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
	void publicModeShowsOnlyPlacesLinkedToPublishedPublicPosts() {
		UUID ownerId = createAccount();
		setDatabaseContext(ownerId, "OWNER");
		UUID publicPlaceId = UUID.randomUUID();
		UUID publicDraftPlaceId = UUID.randomUUID();
		UUID unlistedPlaceId = UUID.randomUUID();
		UUID privatePlaceId = UUID.randomUUID();
		insertPlace(ownerId, publicPlaceId, "public linked place");
		insertPlace(ownerId, publicDraftPlaceId, "public draft linked place");
		insertPlace(ownerId, unlistedPlaceId, "unlisted linked place");
		insertPlace(ownerId, privatePlaceId, "private linked place");
		insertPostAtPlace(ownerId, publicPlaceId, "PUBLIC", "PUBLISHED");
		insertPostAtPlace(ownerId, publicDraftPlaceId, "PUBLIC", "DRAFT");
		insertPostAtPlace(ownerId, unlistedPlaceId, "UNLISTED", "PUBLISHED");
		insertPostAtPlace(ownerId, privatePlaceId, "PRIVATE", "PUBLISHED");

		setDatabaseContext(null, "PUBLIC");
		List<UUID> visiblePlaceIds = jdbcTemplate.queryForList("SELECT id FROM places ORDER BY id", UUID.class);

		assertThat(visiblePlaceIds)
			.containsExactly(publicPlaceId)
			.doesNotContain(publicDraftPlaceId, unlistedPlaceId, privatePlaceId);
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
	void ownerModeHidesEveryOtherOwnersProtectedRow() {
		OwnerGraph ownerAGraph = createOwnerGraph(createAccount());
		OwnerGraph ownerBGraph = createOwnerGraph(createAccount());

		setDatabaseContext(ownerAGraph.ownerId(), "OWNER");

		assertOwnerIsolation("profiles", "user_id", ownerAGraph.ownerId(), ownerBGraph.ownerId());
		assertOwnerIsolation("trips", "id", ownerAGraph.tripId(), ownerBGraph.tripId());
		assertOwnerIsolation("places", "id", ownerAGraph.placeId(), ownerBGraph.placeId());
		assertOwnerIsolation("posts", "id", ownerAGraph.restaurantPostId(), ownerBGraph.restaurantPostId());
		assertOwnerIsolation(
			"restaurant_details",
			"post_id",
			ownerAGraph.restaurantPostId(),
			ownerBGraph.restaurantPostId()
		);
		assertOwnerIsolation(
			"destination_details",
			"post_id",
			ownerAGraph.destinationPostId(),
			ownerBGraph.destinationPostId()
		);
		assertOwnerIsolation("tags", "id", ownerAGraph.tagId(), ownerBGraph.tagId());
		assertOwnerIsolation(
			"post_tags",
			"post_id",
			ownerAGraph.restaurantPostId(),
			ownerBGraph.restaurantPostId()
		);
		assertOwnerIsolation("photos", "id", ownerAGraph.photoId(), ownerBGraph.photoId());
		assertOwnerIsolation("photo_assets", "id", ownerAGraph.assetId(), ownerBGraph.assetId());
		assertOwnerIsolation("upload_batches", "id", ownerAGraph.uploadBatchId(), ownerBGraph.uploadBatchId());
		assertOwnerIsolation("upload_items", "id", ownerAGraph.uploadItemId(), ownerBGraph.uploadItemId());
		assertOwnerIsolation(
			"image_processing_jobs",
			"id",
			ownerAGraph.imageProcessingJobId(),
			ownerBGraph.imageProcessingJobId()
		);
	}

	@Test
	void ownerCannotUpdateOrDeleteAnotherOwnersPost() {
		UUID ownerA = createAccount();
		UUID ownerB = createAccount();
		UUID ownerBPost = createPost(ownerB, "PRIVATE", "DRAFT");
		setDatabaseContext(ownerA, "OWNER");

		assertThat(jdbcTemplate.update(
			"UPDATE posts SET title = ? WHERE id = ?",
			"forged title",
			ownerBPost
		)).isZero();
		assertThat(jdbcTemplate.update("DELETE FROM posts WHERE id = ?", ownerBPost)).isZero();

		setDatabaseContext(ownerB, "OWNER");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT title FROM posts WHERE id = ?",
			String.class,
			ownerBPost
		)).isEqualTo("private post");
	}

	@Test
	void ownerCannotAttachPhotoAssetToAnotherOwnersPhoto() {
		UUID ownerA = createAccount();
		OwnerGraph ownerBGraph = createOwnerGraph(createAccount());
		setDatabaseContext(ownerA, "OWNER");

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied
			) VALUES (?, ?, 'THUMBNAIL', 'PRIVATE', ?, 'image/webp', 320, 240, 100, TRUE, TRUE)
			""",
			UUID.randomUUID(),
			ownerBGraph.photoId(),
			"private/forged-" + UUID.randomUUID() + ".webp"
		)).isInstanceOf(DataAccessException.class);
	}

	@Test
	void ownerCannotQueueAnotherOwnersUploadItem() {
		UUID ownerA = createAccount();
		OwnerGraph ownerBGraph = createOwnerGraph(createAccount());
		UUID ownerAPost = createPost(ownerA, "PRIVATE", "DRAFT");
		setDatabaseContext(ownerA, "OWNER");

		assertThatThrownBy(() -> jdbcTemplate.update(
			"""
			INSERT INTO image_processing_jobs (id, owner_user_id, post_id, upload_item_id)
			VALUES (?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			ownerA,
			ownerAPost,
			ownerBGraph.uploadItemId()
		)).isInstanceOf(DataAccessException.class);
	}

	@Test
	void publicModeCannotModifyPublishedRows() {
		UUID ownerId = createAccount();
		UUID publicPostId = createPost(ownerId, "PUBLIC", "PUBLISHED");
		setDatabaseContext(null, "PUBLIC");

		assertThat(findVisiblePostIds()).containsExactly(publicPostId);
		assertThat(jdbcTemplate.update(
			"UPDATE posts SET title = ? WHERE id = ?",
			"forged public title",
			publicPostId
		)).isZero();
		assertThat(jdbcTemplate.update("DELETE FROM posts WHERE id = ?", publicPostId)).isZero();
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
		assertThat(jdbcTemplate.queryForObject(
			"SELECT has_table_privilege('placesplates_app', 'public.image_processing_jobs', 'SELECT')",
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

	private UUID insertPostAtPlace(UUID ownerId, UUID placeId, String visibility, String status) {
		UUID postId = UUID.randomUUID();
		OffsetDateTime publishedAt = "PUBLISHED".equals(status) ? OffsetDateTime.now() : null;
		jdbcTemplate.update(
			"""
			INSERT INTO posts (
			    id, owner_user_id, place_id, category, title, visibility, status,
			    public_visit_year, public_visit_month, published_at
			) VALUES (?, ?, ?, 'DESTINATION', ?, ?, ?, ?, ?, ?)
			""",
			postId,
			ownerId,
			placeId,
			"place visibility post",
			visibility,
			status,
			publishedAt == null ? null : 2026,
			publishedAt == null ? null : 8,
			publishedAt
		);
		return postId;
	}

	private void insertPlace(UUID ownerId, UUID placeId, String name) {
		jdbcTemplate.update(
			"INSERT INTO places (id, created_by_user_id, source, name) VALUES (?, ?, 'MANUAL', ?)",
			placeId,
			ownerId,
			name
		);
	}

	private PublicPhotoGraph insertReadyPublicPhoto(UUID ownerId, UUID postId) {
		setDatabaseContext(ownerId, "OWNER");
		UUID photoId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO photos (id, owner_user_id, post_id, processing_status) VALUES (?, ?, ?, 'READY')",
			photoId,
			ownerId,
			postId
		);
		jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied,
			    watermark_version, watermark_position
			) VALUES (?, ?, 'PUBLIC_DETAIL', 'PUBLIC', ?, 'image/jpeg',
			    1200, 900, 800, TRUE, TRUE, 'places-plates-corner-v1', 'BOTTOM_RIGHT')
			""",
			assetId,
			photoId,
			"public/" + assetId + ".jpg"
		);
		return new PublicPhotoGraph(photoId, assetId);
	}

	private void insertRestaurantDetails(UUID... postIds) {
		for (UUID postId : postIds) {
			jdbcTemplate.update("INSERT INTO restaurant_details (post_id) VALUES (?)", postId);
		}
	}

	private OwnerGraph createOwnerGraph(UUID ownerId) {
		setDatabaseContext(ownerId, "OWNER");
		String idPart = ownerId.toString();
		UUID tripId = UUID.randomUUID();
		UUID placeId = UUID.randomUUID();
		UUID restaurantPostId = UUID.randomUUID();
		UUID destinationPostId = UUID.randomUUID();
		UUID tagId = UUID.randomUUID();
		UUID photoId = UUID.randomUUID();
		UUID assetId = UUID.randomUUID();
		UUID uploadBatchId = UUID.randomUUID();
		UUID uploadItemId = UUID.randomUUID();
		UUID imageProcessingJobId = UUID.randomUUID();

		jdbcTemplate.update(
			"INSERT INTO profiles (user_id, slug, display_name) VALUES (?, ?, ?)",
			ownerId,
			"profile-" + idPart,
			"test profile"
		);
		jdbcTemplate.update(
			"INSERT INTO trips (id, owner_user_id, title, slug) VALUES (?, ?, ?, ?)",
			tripId,
			ownerId,
			"test trip",
			"trip-" + idPart
		);
		jdbcTemplate.update(
			"INSERT INTO places (id, created_by_user_id, source, name) VALUES (?, ?, 'MANUAL', ?)",
			placeId,
			ownerId,
			"test place"
		);
		jdbcTemplate.update(
			"""
			INSERT INTO posts (id, owner_user_id, trip_id, place_id, category, title)
			VALUES (?, ?, ?, ?, 'RESTAURANT', ?)
			""",
			restaurantPostId,
			ownerId,
			tripId,
			placeId,
			"restaurant draft"
		);
		jdbcTemplate.update(
			"""
			INSERT INTO posts (id, owner_user_id, trip_id, place_id, category, title)
			VALUES (?, ?, ?, ?, 'DESTINATION', ?)
			""",
			destinationPostId,
			ownerId,
			tripId,
			placeId,
			"destination draft"
		);
		jdbcTemplate.update(
			"INSERT INTO restaurant_details (post_id, rating) VALUES (?, 4.5)",
			restaurantPostId
		);
		jdbcTemplate.update(
			"INSERT INTO destination_details (post_id, highlights) VALUES (?, ?)",
			destinationPostId,
			"private highlights"
		);
		jdbcTemplate.update(
			"INSERT INTO tags (id, owner_user_id, tag_type, name, slug) VALUES (?, ?, 'USER', ?, ?)",
			tagId,
			ownerId,
			"test tag",
			"tag-" + idPart
		);
		jdbcTemplate.update(
			"INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)",
			restaurantPostId,
			tagId
		);
		jdbcTemplate.update(
			"""
			INSERT INTO photos (id, owner_user_id, post_id, processing_status)
			VALUES (?, ?, ?, 'READY')
			""",
			photoId,
			ownerId,
			restaurantPostId
		);
		jdbcTemplate.update(
			"""
			INSERT INTO photo_assets (
			    id, photo_id, variant_type, access_level, storage_key, mime_type,
			    width, height, byte_size, metadata_scan_passed, watermark_applied
			) VALUES (?, ?, 'SANITIZED_MASTER', 'PRIVATE', ?, 'image/webp', 1600, 1200, 1000, TRUE, FALSE)
			""",
			assetId,
			photoId,
			"private/" + assetId + ".webp"
		);
		jdbcTemplate.update(
			"""
			INSERT INTO upload_batches (id, owner_user_id, post_id, expires_at)
			VALUES (?, ?, ?, ?)
			""",
			uploadBatchId,
			ownerId,
			restaurantPostId,
			OffsetDateTime.now().plusHours(1)
		);
		jdbcTemplate.update(
			"""
			INSERT INTO upload_items (id, upload_batch_id, temporary_storage_key, expires_at)
			VALUES (?, ?, ?, ?)
			""",
			uploadItemId,
			uploadBatchId,
			"temporary/" + uploadItemId + ".jpg",
			OffsetDateTime.now().plusHours(1)
		);
		jdbcTemplate.update(
			"""
			INSERT INTO image_processing_jobs (id, owner_user_id, post_id, upload_item_id)
			VALUES (?, ?, ?, ?)
			""",
			imageProcessingJobId,
			ownerId,
			restaurantPostId,
			uploadItemId
		);

		return new OwnerGraph(
			ownerId,
			tripId,
			placeId,
			restaurantPostId,
			destinationPostId,
			tagId,
			photoId,
			assetId,
			uploadBatchId,
			uploadItemId,
			imageProcessingJobId
		);
	}

	private void assertOwnerIsolation(String tableName, String idColumn, UUID ownId, UUID otherOwnerId) {
		List<UUID> visibleIds = jdbcTemplate.queryForList(
			"SELECT " + idColumn + " FROM " + tableName + " ORDER BY " + idColumn,
			UUID.class
		);
		assertThat(visibleIds).contains(ownId).doesNotContain(otherOwnerId);
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

	private record PublicPhotoGraph(UUID photoId, UUID assetId) {
	}

	private record OwnerGraph(
		UUID ownerId,
		UUID tripId,
		UUID placeId,
		UUID restaurantPostId,
		UUID destinationPostId,
		UUID tagId,
		UUID photoId,
		UUID assetId,
		UUID uploadBatchId,
		UUID uploadItemId,
		UUID imageProcessingJobId
	) {
	}
}
