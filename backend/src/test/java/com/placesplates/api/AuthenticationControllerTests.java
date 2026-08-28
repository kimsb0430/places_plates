package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationControllerTests {

	private static final String ADMIN_EMAIL = "administrator@example.test";
	private static final String ADMIN_PASSWORD = "local-test-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		accountRepository.deleteAll();
		accountRepository.save(AdministratorAccount.create(
			ADMIN_EMAIL,
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
	}

	@Test
	void csrfTokenIsPubliclyAvailable() throws Exception {
		mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
			.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void publicApiBoundaryDoesNotRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/public/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void errorDispatchPreservesTheFrameworkErrorStatusWithoutLogin() throws Exception {
		mockMvc.perform(servletContext -> {
			MockHttpServletRequest request = get("/error").buildRequest(servletContext);
			request.setDispatcherType(DispatcherType.ERROR);
			request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
			request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/public/posts");
			return request;
		})
			.andExpect(status().isBadRequest());
	}

	@Test
	void ownerApiBoundaryRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/manage/missing"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void loginRestoresSessionAndLogoutInvalidatesIt() throws Exception {
		CsrfSession csrfSession = getCsrfSession();
		String sessionIdBeforeLogin = csrfSession.cookie().getValue();
		MvcResult loginResult = performLogin(csrfSession, ADMIN_EMAIL, ADMIN_PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andReturn();
		Cookie authenticatedCookie = requireSessionCookie(loginResult);
		assertThat(authenticatedCookie.getValue()).isNotEqualTo(sessionIdBeforeLogin);
		CsrfSession authenticatedSession = csrfSession.withCookie(authenticatedCookie);

		mockMvc.perform(get("/api/v1/auth/session").cookie(authenticatedSession.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL));

		mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(authenticatedSession.cookie())
				.header(authenticatedSession.headerName(), authenticatedSession.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/auth/session").cookie(authenticatedSession.cookie()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void invalidCredentialsReturnPredictableError() throws Exception {
		CsrfSession csrfSession = getCsrfSession();
		performLogin(csrfSession, ADMIN_EMAIL, "incorrect-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

		mockMvc.perform(get("/api/v1/auth/session").cookie(csrfSession.cookie()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void memberAccountCannotUseAdministratorLogin() throws Exception {
		jdbcTemplate.update(
			"INSERT INTO app_users (id, email, password_hash) VALUES (?, ?, ?)",
			UUID.randomUUID(),
			"member@example.test",
			passwordEncoder.encode(ADMIN_PASSWORD)
		);
		CsrfSession csrfSession = getCsrfSession();

		performLogin(csrfSession, "member@example.test", ADMIN_PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"SUSPENDED", "DEACTIVATED"})
	void inactiveAdministratorCannotLogin(String accountStatus) throws Exception {
		jdbcTemplate.update(
			"UPDATE app_users SET status = ? WHERE email = ?",
			accountStatus,
			ADMIN_EMAIL
		);
		CsrfSession csrfSession = getCsrfSession();

		performLogin(csrfSession, ADMIN_EMAIL, ADMIN_PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
	}

	@Test
	void logoutWithoutCsrfTokenDoesNotInvalidateAuthenticatedSession() throws Exception {
		CsrfSession authenticatedSession = loginSuccessfully();

		mockMvc.perform(post("/api/v1/auth/logout").cookie(authenticatedSession.cookie()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

		mockMvc.perform(get("/api/v1/auth/session").cookie(authenticatedSession.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"administrator@example.test","password":"local-test-password"}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
	}

	private CsrfSession getCsrfSession() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		String responseBody = result.getResponse().getContentAsString();
		return new CsrfSession(
			requireSessionCookie(result),
			JsonPath.read(responseBody, "$.headerName"),
			JsonPath.read(responseBody, "$.token")
		);
	}

	private CsrfSession loginSuccessfully() throws Exception {
		CsrfSession csrfSession = getCsrfSession();
		MvcResult loginResult = performLogin(csrfSession, ADMIN_EMAIL, ADMIN_PASSWORD)
			.andExpect(status().isOk())
			.andReturn();
		return csrfSession.withCookie(requireSessionCookie(loginResult));
	}

	private Cookie requireSessionCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("SESSION");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	private org.springframework.test.web.servlet.ResultActions performLogin(
		CsrfSession csrfSession,
		String email,
		String password
	) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.cookie(csrfSession.cookie())
			.header(csrfSession.headerName(), csrfSession.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s","password":"%s"}
				""".formatted(email, password)));
	}

	private record CsrfSession(Cookie cookie, String headerName, String token) {

		private CsrfSession withCookie(Cookie nextCookie) {
			return new CsrfSession(nextCookie, headerName, token);
		}
	}
}
