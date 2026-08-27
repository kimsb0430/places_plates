package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.place.dto.PlaceSearchResult;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.place.service.PlaceSearchGateway;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.repository.DraftPostRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class PlaceControllerTests {

	private static final String ADMIN_PASSWORD = "local-place-password";

	@Autowired private MockMvc mockMvc;
	@Autowired private AdministratorAccountRepository accountRepository;
	@Autowired private DraftPostRepository draftPostRepository;
	@Autowired private PlaceRepository placeRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	@MockitoBean
	private PlaceSearchGateway placeSearchGateway;

	private String adminEmail;
	private AdministratorAccount administrator;

	@BeforeEach
	void setUp() {
		adminEmail = "place-admin-" + UUID.randomUUID() + "@example.test";
		administrator = accountRepository.save(AdministratorAccount.create(
			adminEmail,
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
	}

	@Test
	void searchesGooglePlacesOnlyForAuthenticatedOwner() throws Exception {
		AuthenticatedSession authenticated = login();
		when(placeSearchGateway.search("니시키 시장")).thenReturn(List.of(new PlaceSearchResult(
			"google-place-1",
			"니시키 시장",
			"market",
			"일본 교토부 교토시",
			new BigDecimal("35.005000"),
			new BigDecimal("135.764000"),
			"https://maps.google.com/example"
		)));

		mockMvc.perform(get("/api/v1/manage/places/search")
				.cookie(authenticated.cookie())
				.param("query", "니시키 시장"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].googlePlaceId").value("google-place-1"))
			.andExpect(jsonPath("$[0].name").value("니시키 시장"))
			.andExpect(jsonPath("$[0].latitude").value(35.005));

		mockMvc.perform(get("/api/v1/manage/places/search").param("query", "니시키 시장"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void connectsAndDisconnectsGooglePlace() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(), PostCategory.RESTAURANT
		));

		mockMvc.perform(put("/api/v1/manage/drafts/{draftId}/place", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "source": "GOOGLE",
					  "googlePlaceId": "ChIJ-c21-place",
					  "name": "니시키 시장",
					  "placeType": "market",
					  "formattedAddress": "일본 교토부 교토시",
					  "latitude": 35.005,
					  "longitude": 135.764
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.place.source").value("GOOGLE"))
			.andExpect(jsonPath("$.place.googlePlaceId").value("ChIJ-c21-place"))
			.andExpect(jsonPath("$.place.googleMapsUrl").value(
				org.hamcrest.Matchers.containsString("query_place_id=ChIJ-c21-place")
			));

		assertThat(placeRepository.findByGooglePlaceId("ChIJ-c21-place")).isPresent();

		mockMvc.perform(delete("/api/v1/manage/drafts/{draftId}/place", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.place").doesNotExist());
	}

	@Test
	void savesManualPlaceAndRejectsIncompleteCoordinates() throws Exception {
		AuthenticatedSession authenticated = login();
		DraftPost draft = draftPostRepository.save(DraftPost.create(
			administrator.getId(), PostCategory.DESTINATION
		));

		mockMvc.perform(put("/api/v1/manage/drafts/{draftId}/place", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"source":"MANUAL","name":"골목 전망대","latitude":35.1}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PLACE_CONNECTION_INVALID"));

		mockMvc.perform(put("/api/v1/manage/drafts/{draftId}/place", draft.getId())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "source":"MANUAL",
					  "name":"골목 전망대",
					  "formattedAddress":"교토의 작은 골목",
					  "latitude":35.100001,
					  "longitude":135.700001
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.place.source").value("MANUAL"))
			.andExpect(jsonPath("$.place.name").value("골목 전망대"))
			.andExpect(jsonPath("$.place.latitude").value(35.100001));
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

	private record AuthenticatedSession(Cookie cookie, String headerName, String token) { }
}
