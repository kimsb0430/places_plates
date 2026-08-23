package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;

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
	void loginRestoresSessionAndLogoutInvalidatesIt() throws Exception {
		CsrfSession csrfSession = getCsrfSession();
		String sessionIdBeforeLogin = csrfSession.session().getId();
		mockMvc.perform(post("/api/v1/auth/login")
				.session(csrfSession.session())
				.header(csrfSession.headerName(), csrfSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"administrator@example.test","password":"local-test-password"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
			.andExpect(jsonPath("$.role").value("ADMIN"));
		assertThat(csrfSession.session().getId()).isNotEqualTo(sessionIdBeforeLogin);

		mockMvc.perform(get("/api/v1/auth/session").session(csrfSession.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL));

		mockMvc.perform(post("/api/v1/auth/logout")
				.session(csrfSession.session())
				.header(csrfSession.headerName(), csrfSession.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/auth/session"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void invalidCredentialsReturnPredictableError() throws Exception {
		CsrfSession csrfSession = getCsrfSession();
		mockMvc.perform(post("/api/v1/auth/login")
				.session(csrfSession.session())
				.header(csrfSession.headerName(), csrfSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"administrator@example.test","password":"incorrect-password"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
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

		mockMvc.perform(post("/api/v1/auth/login")
				.session(csrfSession.session())
				.header(csrfSession.headerName(), csrfSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"member@example.test","password":"local-test-password"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
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
			(MockHttpSession) result.getRequest().getSession(false),
			JsonPath.read(responseBody, "$.headerName"),
			JsonPath.read(responseBody, "$.token")
		);
	}

	private record CsrfSession(MockHttpSession session, String headerName, String token) {
	}
}
