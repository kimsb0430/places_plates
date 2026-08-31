package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.infra.storage.PrivatePhotoStorage;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class DraftPhotoControllerTests {

	private static final String ADMIN_EMAIL = "draft-photo-admin@example.test";
	private static final String ADMIN_PASSWORD = "local-draft-photo-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private DraftPostRepository draftPostRepository;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PhotoAssetRepository photoAssetRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PlaceRepository placeRepository;

	@MockitoBean
	private PrivatePhotoStorage privatePhotoStorage;

	private AdministratorAccount administrator;

	@BeforeEach
	void setUp() {
		photoAssetRepository.deleteAll();
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
	void listsAndUpdatesOwnedDraftPhotosInSubmittedOrder() throws Exception {
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));
		Photo first = readyPhoto(draft.getId());
		Photo second = readyPhoto(draft.getId());
		addThumbnail(first, "variants/first/thumbnail.jpg");
		addThumbnail(second, "variants/second/thumbnail.jpg");
		AuthenticatedSession session = login();

		mockMvc.perform(get(photoPath(draft.getId())).cookie(session.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].thumbnailPath").isNotEmpty());

		mockMvc.perform(put(photoPath(draft.getId()))
				.cookie(session.cookie())
				.header(session.headerName(), session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"photos":[
					  {"photoId":"%s","cover":true,"altText":"  비 온 뒤의 정원  "},
					  {"photoId":"%s","cover":false,"altText":"두 번째 장면"}
					]}
					""".formatted(second.getId(), first.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(second.getId().toString()))
			.andExpect(jsonPath("$[0].displayOrder").value(0))
			.andExpect(jsonPath("$[0].cover").value(true))
			.andExpect(jsonPath("$[0].altText").value("비 온 뒤의 정원"))
			.andExpect(jsonPath("$[1].displayOrder").value(1));

		Photo savedFirst = photoRepository.findById(first.getId()).orElseThrow();
		Photo savedSecond = photoRepository.findById(second.getId()).orElseThrow();
		assertThat(savedFirst.getDisplayOrder()).isEqualTo(1);
		assertThat(savedFirst.isCover()).isFalse();
		assertThat(savedSecond.getDisplayOrder()).isZero();
		assertThat(savedSecond.isCover()).isTrue();
		assertThat(savedSecond.getAltText()).isEqualTo("비 온 뒤의 정원");
	}

	@Test
	void returnsOwnedThumbnailWithoutPublicCaching() throws Exception {
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));
		Photo photo = readyPhoto(draft.getId());
		String storageKey = "variants/private/thumbnail.jpg";
		addThumbnail(photo, storageKey);
		byte[] thumbnail = "private-thumbnail".getBytes(StandardCharsets.UTF_8);
		when(privatePhotoStorage.downloadResponsiveVariant(storageKey)).thenReturn(thumbnail);
		AuthenticatedSession session = login();

		mockMvc.perform(get(photoPath(draft.getId()) + "/" + photo.getId() + "/thumbnail")
				.cookie(session.cookie()))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().bytes(thumbnail));
	}

	@Test
	void rejectsPartialOrForeignPhotoSets() throws Exception {
		DraftPost ownedDraft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));
		Photo first = readyPhoto(ownedDraft.getId());
		readyPhoto(ownedDraft.getId());
		AdministratorAccount other = accountRepository.save(AdministratorAccount.create(
			"other-photo-owner@example.test",
			passwordEncoder.encode("other-password")
		));
		DraftPost otherDraft = draftPostRepository.save(DraftPost.create(
			other.getId(),
			PostCategory.DESTINATION
		));
		AuthenticatedSession session = login();

		mockMvc.perform(put(photoPath(ownedDraft.getId()))
				.cookie(session.cookie())
				.header(session.headerName(), session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"photos":[{"photoId":"%s","cover":true,"altText":null}]}
					""".formatted(first.getId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DRAFT_PHOTO_INVALID_SET"));

		mockMvc.perform(get(photoPath(otherDraft.getId())).cookie(session.cookie()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DRAFT_PHOTO_NOT_FOUND"));
	}

	@Test
	void updatesCoverAndOrderForOwnedPublishedPhotos() throws Exception {
		DraftPost post = DraftPost.create(administrator.getId(), PostCategory.DESTINATION);
		Place place = placeRepository.save(Place.manual(
			administrator.getId(), "게시 사진 장소", null, null, null, null
		));
		post.updateEditorFields(post.getTitle(), null, null, 2026, 8);
		post.connectPlace(place.getId());
		post.publish(PostVisibility.PUBLIC);
		post = draftPostRepository.save(post);
		Photo first = readyPhoto(post.getId());
		Photo second = readyPhoto(post.getId());
		addThumbnail(first, "variants/published/first-thumbnail.jpg");
		addThumbnail(second, "variants/published/second-thumbnail.jpg");
		AuthenticatedSession session = login();

		String path = publishedPhotoPath(post.getId());
		mockMvc.perform(put(path)
				.cookie(session.cookie())
				.header(session.headerName(), session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"photos":[
					  {"photoId":"%s","cover":true,"altText":"새 대표 사진"},
					  {"photoId":"%s","cover":false,"altText":"기존 사진"}
					]}
					""".formatted(second.getId(), first.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(second.getId().toString()))
			.andExpect(jsonPath("$[0].cover").value(true))
			.andExpect(jsonPath("$[0].thumbnailPath")
				.value(org.hamcrest.Matchers.containsString("/api/v1/manage/posts/")));

		assertThat(photoRepository.findById(second.getId()).orElseThrow().isCover()).isTrue();
		assertThat(photoRepository.findById(first.getId()).orElseThrow().isCover()).isFalse();
	}

	private Photo readyPhoto(UUID draftId) {
		Photo photo = Photo.processing(administrator.getId(), draftId);
		photo.markReady();
		return photoRepository.save(photo);
	}

	private void addThumbnail(Photo photo, String storageKey) {
		photoAssetRepository.save(PhotoAsset.privateResponsiveVariant(
			photo.getId(),
			PhotoAssetVariantType.THUMBNAIL,
			storageKey,
			"image/jpeg",
			480,
			320,
			1024
		));
	}

	private AuthenticatedSession login() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		String csrfBody = csrfResult.getResponse().getContentAsString();
		Cookie csrfCookie = requireSessionCookie(csrfResult);
		String headerName = JsonPath.read(csrfBody, "$.headerName");
		String token = JsonPath.read(csrfBody, "$.token");

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrfCookie)
				.header(headerName, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"draft-photo-admin@example.test","password":"local-draft-photo-password"}
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

	private static String photoPath(UUID draftId) {
		return "/api/v1/manage/drafts/" + draftId + "/photos";
	}

	private static String publishedPhotoPath(UUID postId) {
		return "/api/v1/manage/posts/" + postId + "/photos";
	}

	private record AuthenticatedSession(Cookie cookie, String headerName, String token) {
	}
}
