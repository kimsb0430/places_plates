package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.placesplates.domain.post.repository.DraftPostRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class DraftPostControllerTests {

	private static final String ADMIN_PASSWORD = "local-draft-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private DraftPostRepository draftPostRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String adminEmail;
	private AdministratorAccount administrator;

	@BeforeEach
	void setUp() {
		adminEmail = "draft-admin-" + UUID.randomUUID() + "@example.test";
		administrator = accountRepository.save(AdministratorAccount.create(
			adminEmail,
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
	}

	@Test
	void updatesAndReloadsCommonEditorFields() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "기온의 늦은 점심",
					  "summary": "걷다 우연히 만난 따뜻한 한 끼",
					  "content": "비 오는 날의 기억을 천천히 기록했다.",
					  "publicVisitYear": 2026,
					  "publicVisitMonth": 4
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("기온의 늦은 점심"))
			.andExpect(jsonPath("$.summary").value("걷다 우연히 만난 따뜻한 한 끼"))
			.andExpect(jsonPath("$.content").value("비 오는 날의 기억을 천천히 기록했다."))
			.andExpect(jsonPath("$.publicVisitYear").value(2026))
			.andExpect(jsonPath("$.publicVisitMonth").value(4));

		mockMvc.perform(get("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("기온의 늦은 점심"))
			.andExpect(jsonPath("$.summary").value("걷다 우연히 만난 따뜻한 한 끼"))
			.andExpect(jsonPath("$.content").value("비 오는 날의 기억을 천천히 기록했다."))
			.andExpect(jsonPath("$.publicVisitYear").value(2026))
			.andExpect(jsonPath("$.publicVisitMonth").value(4));
	}

	@Test
	void updatesAndReloadsRestaurantEditorFields() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "니시키 시장의 우동",
					  "restaurantDetails": {
					    "rating": 4.5,
					    "recommendedMenu": "새우 튀김 우동",
					    "priceRange": "MODERATE",
					    "waitingMinutes": 20,
					    "revisitIntention": "YES"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restaurantDetails.rating").value(4.5))
			.andExpect(jsonPath("$.restaurantDetails.recommendedMenu").value("새우 튀김 우동"))
			.andExpect(jsonPath("$.restaurantDetails.priceRange").value("MODERATE"))
			.andExpect(jsonPath("$.restaurantDetails.waitingMinutes").value(20))
			.andExpect(jsonPath("$.restaurantDetails.revisitIntention").value("YES"));

		mockMvc.perform(get("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restaurantDetails.rating").value(4.5))
			.andExpect(jsonPath("$.restaurantDetails.recommendedMenu").value("새우 튀김 우동"))
			.andExpect(jsonPath("$.restaurantDetails.priceRange").value("MODERATE"))
			.andExpect(jsonPath("$.restaurantDetails.waitingMinutes").value(20))
			.andExpect(jsonPath("$.restaurantDetails.revisitIntention").value("YES"));
	}

	@Test
	void preservesRestaurantFieldsWhenLegacyRequestOmitsThem() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "기존 맛집 초안",
					  "restaurantDetails": {"rating": 5.0, "revisitIntention": "YES"}
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{" +
					"\"title\":\"공통 필드만 수정\"" +
					"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restaurantDetails.rating").value(5.0))
			.andExpect(jsonPath("$.restaurantDetails.revisitIntention").value("YES"));
	}

	@Test
	void clearsRestaurantFieldsWhenAllValuesAreEmpty() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "비울 맛집 초안",
					  "restaurantDetails": {"rating": 3.0, "recommendedMenu": "라멘"}
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "비운 맛집 초안",
					  "restaurantDetails": {
					    "rating": null,
					    "recommendedMenu": " ",
					    "priceRange": null,
					    "waitingMinutes": null,
					    "revisitIntention": null
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.restaurantDetails").doesNotExist());
	}

	@Test
	void rejectsRestaurantFieldsForDestinationDraft() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "여행지 초안",
					  "restaurantDetails": {"rating": 4.0}
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DRAFT_POST_RESTAURANT_FIELDS_INVALID"));
	}

	@Test
	void rejectsInvalidRestaurantFieldValues() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "검증할 맛집 초안",
					  "restaurantDetails": {"rating": 5.5, "waitingMinutes": -1}
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updatesAndReloadsDestinationEditorFields() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "새벽의 후시미 이나리",
					  "destinationDetails": {
					    "recommendedTime": "해 뜨기 전 이른 아침",
					    "durationMinutes": 120,
					    "highlights": "붉은 도리이와 산길",
					    "travelTips": "편한 신발과 물을 준비한다."
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.destinationDetails.recommendedTime").value("해 뜨기 전 이른 아침"))
			.andExpect(jsonPath("$.destinationDetails.durationMinutes").value(120))
			.andExpect(jsonPath("$.destinationDetails.highlights").value("붉은 도리이와 산길"))
			.andExpect(jsonPath("$.destinationDetails.travelTips").value("편한 신발과 물을 준비한다."));

		mockMvc.perform(get("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.destinationDetails.recommendedTime").value("해 뜨기 전 이른 아침"))
			.andExpect(jsonPath("$.destinationDetails.durationMinutes").value(120))
			.andExpect(jsonPath("$.destinationDetails.highlights").value("붉은 도리이와 산길"))
			.andExpect(jsonPath("$.destinationDetails.travelTips").value("편한 신발과 물을 준비한다."));
	}

	@Test
	void preservesDestinationFieldsWhenLegacyRequestOmitsThem() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "기존 여행지 초안",
					  "destinationDetails": {"durationMinutes": 90, "highlights": "정원"}
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"공통 필드만 수정\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.destinationDetails.durationMinutes").value(90))
			.andExpect(jsonPath("$.destinationDetails.highlights").value("정원"));
	}

	@Test
	void clearsDestinationFieldsWhenAllValuesAreEmpty() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "비울 여행지 초안",
					  "destinationDetails": {"recommendedTime": "오전", "durationMinutes": 60}
					}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "비운 여행지 초안",
					  "destinationDetails": {
					    "recommendedTime": " ",
					    "durationMinutes": null,
					    "highlights": "",
					    "travelTips": null
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.destinationDetails").doesNotExist());
	}

	@Test
	void rejectsDestinationFieldsForRestaurantDraft() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "맛집 초안",
					  "destinationDetails": {"durationMinutes": 30}
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DRAFT_POST_DESTINATION_FIELDS_INVALID"));
	}

	@Test
	void rejectsInvalidDestinationFieldValues() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "검증할 여행지 초안",
					  "destinationDetails": {"durationMinutes": -1}
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void savesPartialDraftWithOptionalFieldsCleared() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "새벽의 후시미 이나리",
					  "summary": "  ",
					  "content": "",
					  "publicVisitYear": null,
					  "publicVisitMonth": null
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("새벽의 후시미 이나리"))
			.andExpect(jsonPath("$.summary").doesNotExist())
			.andExpect(jsonPath("$.content").doesNotExist())
			.andExpect(jsonPath("$.publicVisitYear").doesNotExist())
			.andExpect(jsonPath("$.publicVisitMonth").doesNotExist());
	}

	@Test
	void rejectsVisitYearWithoutMonth() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(),
			PostCategory.RESTAURANT
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "방문 기록",
					  "publicVisitYear": 2026,
					  "publicVisitMonth": null
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DRAFT_POST_VISIT_MONTH_INVALID"));
	}

	@Test
	void hidesAnotherOwnersDraftDuringUpdate() throws Exception {
		AuthenticatedSession authenticated = login();
		AdministratorAccount anotherOwner = accountRepository.save(AdministratorAccount.create(
			"another-draft-owner-" + UUID.randomUUID() + "@example.test",
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
		DraftPost anotherDraft = draftPostRepository.save(DraftPost.create(
			anotherOwner.getId(),
			PostCategory.DESTINATION
		));

		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", anotherDraft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"수정하면 안 되는 초안\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DRAFT_POST_NOT_FOUND"));
	}

	@Test
	void rejectsUnauthenticatedDraftUpdate() throws Exception {
		mockMvc.perform(patch("/api/v1/manage/drafts/{draftId}", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"인증되지 않은 수정\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void allowsPatchCorsPreflightFromFrontend() throws Exception {
		mockMvc.perform(options("/api/v1/manage/drafts/{draftId}", UUID.randomUUID())
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "PATCH")
				.header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
			.andExpect(header().string(
				"Access-Control-Allow-Methods",
				org.hamcrest.Matchers.containsString("PATCH")
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

	private record AuthenticatedSession(Cookie cookie, String headerName, String token) {
	}
}
