package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.entity.UploadBatch;
import com.placesplates.domain.photo.entity.UploadItem;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadBatchRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class DraftPublicationControllerTests {

	private static final String ADMIN_EMAIL = "publication-admin@example.test";
	private static final String ADMIN_PASSWORD = "local-publication-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private DraftPostRepository draftPostRepository;

	@Autowired
	private PlaceRepository placeRepository;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PhotoAssetRepository photoAssetRepository;

	@Autowired
	private UploadBatchRepository uploadBatchRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private AdministratorAccount administrator;

	@BeforeEach
	void setUp() {
		photoAssetRepository.deleteAll();
		uploadBatchRepository.deleteAll();
		photoRepository.deleteAll();
		draftPostRepository.deleteAll();
		placeRepository.deleteAll();
		accountRepository.deleteAll();
		administrator = accountRepository.save(AdministratorAccount.create(
			ADMIN_EMAIL,
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
	}

	@Test
	void blocksPublicationUntilEveryInputAndPhotoSafetyCheckPasses() throws Exception {
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));
		AuthenticatedSession session = login();

		mockMvc.perform(get(readinessPath(draft.getId())).cookie(session.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ready").value(false))
			.andExpect(jsonPath("$.checks[2].code").value("SUMMARY"))
			.andExpect(jsonPath("$.checks[2].passed").value(false))
			.andExpect(jsonPath("$.checks[8].code").value("PUBLIC_ASSETS"))
			.andExpect(jsonPath("$.checks[8].passed").value(false));

		mockMvc.perform(post(publicationPath(draft.getId()))
				.cookie(session.cookie())
				.header(session.headerName(), session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"visibility\":\"PUBLIC\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POST_PUBLICATION_NOT_READY"));

		DraftPost saved = draftPostRepository.findById(draft.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(PostStatus.DRAFT);
		assertThat(saved.getPublishedAt()).isNull();
	}

	@Test
	void publishesSafeDraftWithSelectedVisibility() throws Exception {
		DraftPost draft = createSafeDraft();
		AuthenticatedSession session = login();

		mockMvc.perform(get(readinessPath(draft.getId())).cookie(session.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ready").value(true))
			.andExpect(jsonPath("$.checks[?(@.passed == false)]").isEmpty());

		mockMvc.perform(post(publicationPath(draft.getId()))
				.cookie(session.cookie())
				.header(session.headerName(), session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"visibility\":\"UNLISTED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(draft.getId().toString()))
			.andExpect(jsonPath("$.visibility").value("UNLISTED"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.publishedAt").isNotEmpty());

		DraftPost published = draftPostRepository.findById(draft.getId()).orElseThrow();
		assertThat(published.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(published.getVisibility()).isEqualTo(PostVisibility.UNLISTED);
		assertThat(published.getPublishedAt()).isNotNull();
	}

	@Test
	void requiresAuthenticationAndCsrfProtection() throws Exception {
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(get(readinessPath(draft.getId())))
			.andExpect(status().isUnauthorized());

		AuthenticatedSession session = login();
		mockMvc.perform(post(publicationPath(draft.getId()))
				.cookie(session.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"visibility\":\"PRIVATE\"}"))
			.andExpect(status().isForbidden());
	}

	private DraftPost createSafeDraft() {
		Place place = placeRepository.save(Place.manual(
			administrator.getId(),
			"철학의 길",
			"Kyoto",
			null,
			null,
			"https://www.google.com/maps/search/?api=1&query=Kyoto"
		));
		DraftPost draft = DraftPost.create(administrator.getId(), PostCategory.DESTINATION);
		draft.updateEditorFields("봄날의 산책", "조용히 오래 걷고 싶은 길", null, 2026, 4);
		draft.connectPlace(place.getId());
		draftPostRepository.save(draft);

		Photo photo = Photo.processing(administrator.getId(), draft.getId());
		photo.markReady();
		photo.updateEditorState(0, true, "벚꽃이 핀 산책길");
		photoRepository.save(photo);
		saveSafeAssets(photo.getId());
		saveCompletedUpload(draft.getId(), photo.getId());
		return draft;
	}

	private void saveSafeAssets(UUID photoId) {
		String keyPrefix = "publication/" + photoId + "/";
		photoAssetRepository.save(PhotoAsset.sanitizedMaster(
			photoId, keyPrefix + "master.jpg", "image/jpeg", 1600, 1200, 2000
		));
		for (PhotoAssetVariantType type : new PhotoAssetVariantType[] {
			PhotoAssetVariantType.THUMBNAIL,
			PhotoAssetVariantType.MAP_CARD,
			PhotoAssetVariantType.PUBLIC_DETAIL
		}) {
			photoAssetRepository.save(PhotoAsset.publicWatermarkedVariant(
				photoId,
				type,
				keyPrefix + type.name().toLowerCase() + ".jpg",
				"image/jpeg",
				800,
				600,
				1000,
				"places-plates-corner-v1",
				"BOTTOM_RIGHT"
			));
		}
	}

	private void saveCompletedUpload(UUID draftId, UUID photoId) {
		OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
		UploadBatch batch = UploadBatch.create(administrator.getId(), expiresAt);
		batch.assignPost(draftId);
		UploadItem item = UploadItem.create("private-photo.jpg", "image/jpeg", 3000, expiresAt);
		batch.addItem(item);
		item.start("temporary/" + item.getId());
		item.markUploaded();
		item.assignResultPhoto(photoId);
		item.completeAndForgetOriginal(OffsetDateTime.now(ZoneOffset.UTC));
		uploadBatchRepository.save(batch);
	}

	private AuthenticatedSession login() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		String csrfBody = csrfResult.getResponse().getContentAsString();
		String headerName = JsonPath.read(csrfBody, "$.headerName");
		String token = JsonPath.read(csrfBody, "$.token");
		Cookie csrfCookie = requireSessionCookie(csrfResult);

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrfCookie)
				.header(headerName, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"publication-admin@example.test","password":"local-publication-password"}
					"""))
			.andExpect(status().isOk())
			.andReturn();
		return new AuthenticatedSession(requireSessionCookie(loginResult), headerName, token);
	}

	private Cookie requireSessionCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("SESSION");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	private static String readinessPath(UUID draftId) {
		return "/api/v1/manage/drafts/" + draftId + "/publication-readiness";
	}

	private static String publicationPath(UUID draftId) {
		return "/api/v1/manage/drafts/" + draftId + "/publication";
	}

	private record AuthenticatedSession(Cookie cookie, String headerName, String token) {
	}
}
