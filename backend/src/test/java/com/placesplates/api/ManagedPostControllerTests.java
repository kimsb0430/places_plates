package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class ManagedPostControllerTests {

	private static final String ADMIN_PASSWORD = "local-published-editor-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private DraftPostRepository postRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PlaceRepository placeRepository;

	private AdministratorAccount administrator;
	private String adminEmail;

	@BeforeEach
	void setUp() {
		adminEmail = "published-editor-" + UUID.randomUUID() + "@example.test";
		administrator = accountRepository.save(AdministratorAccount.create(
			adminEmail,
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
	}

	@Test
	void loadsAndUpdatesOwnedPublishedPost() throws Exception {
		DraftPost post = DraftPost.create(administrator.getId(), PostCategory.RESTAURANT);
		post.updateEditorFields("기존 기록", "기존 한줄평", "기존 본문", 2026, 4);
		publish(post);
		postRepository.save(post);
		AuthenticatedSession session = login();

		mockMvc.perform(get("/api/v1/manage/posts/{postId}", post.getId())
				.cookie(session.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.title").value("기존 기록"));

		mockMvc.perform(patch("/api/v1/manage/posts/{postId}", post.getId())
				.cookie(session.cookie())
				.header(session.headerName(), session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "수정된 공개 기록",
					  "summary": "수정된 한줄평",
					  "content": "수정된 본문",
					  "publicVisitYear": 2026,
					  "publicVisitMonth": 8
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("수정된 공개 기록"))
			.andExpect(jsonPath("$.summary").value("수정된 한줄평"))
			.andExpect(jsonPath("$.content").value("수정된 본문"))
			.andExpect(jsonPath("$.publicVisitMonth").value(8))
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		assertThat(postRepository.findById(post.getId()).orElseThrow().getTitle())
			.isEqualTo("수정된 공개 기록");
	}

	@Test
	void hidesAnotherOwnersPublishedPost() throws Exception {
		AdministratorAccount anotherOwner = accountRepository.save(AdministratorAccount.create(
			"another-published-owner@example.test",
			passwordEncoder.encode("another-owner-password")
		));
		DraftPost post = DraftPost.create(anotherOwner.getId(), PostCategory.DESTINATION);
		Place place = placeRepository.save(Place.manual(
			anotherOwner.getId(), "다른 소유자의 장소", null, null, null, null
		));
		post.updateEditorFields(post.getTitle(), null, null, 2026, 8);
		post.connectPlace(place.getId());
		post.publish(PostVisibility.PUBLIC);
		postRepository.save(post);
		AuthenticatedSession session = login();

		mockMvc.perform(get("/api/v1/manage/posts/{postId}", post.getId())
				.cookie(session.cookie()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MANAGED_POST_NOT_FOUND"));
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
					{"email":"%s","password":"%s"}
					""".formatted(adminEmail, ADMIN_PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		return new AuthenticatedSession(requireSessionCookie(loginResult), headerName, token);
	}

	private Cookie requireSessionCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("SESSION");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	private void publish(DraftPost post) {
		Place place = placeRepository.save(Place.manual(
			post.getOwnerUserId(), "게시 장소", null, null, null, null
		));
		post.connectPlace(place.getId());
		post.publish(PostVisibility.PUBLIC);
	}

	private record AuthenticatedSession(Cookie cookie, String headerName, String token) {
	}
}
