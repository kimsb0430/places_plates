package com.placesplates.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.photo.repository.UploadBatchRepository;
import com.placesplates.infra.storage.SignedUploadTicket;
import com.placesplates.infra.storage.TemporaryUploadSigner;

@SpringBootTest
@AutoConfigureMockMvc
class PhotoUploadControllerTests {

	private static final String ADMIN_EMAIL = "photo-admin@example.test";
	private static final String ADMIN_PASSWORD = "local-photo-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private UploadBatchRepository uploadBatchRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private TemporaryUploadSigner uploadSigner;

	@BeforeEach
	void setUp() {
		uploadBatchRepository.deleteAll();
		accountRepository.deleteAll();
		accountRepository.save(AdministratorAccount.create(
			ADMIN_EMAIL,
			passwordEncoder.encode(ADMIN_PASSWORD)
		));
		when(uploadSigner.issue(anyString()))
			.thenAnswer(invocation -> new SignedUploadTicket(
				"https://storage.example.test/upload/resumable/sign",
				"signed-token",
				"temporary-uploads",
				invocation.getArgument(0)
			));
		when(uploadSigner.objectMatches(anyString(), anyLong())).thenReturn(true);
	}

	@Test
	void unauthenticatedUploadIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"files":[{"clientFileName":"photo.jpg","mimeType":"image/jpeg","byteSize":1024}]}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void createsTracksRetriesAndCompletesMultipleUploads() throws Exception {
		AuthenticatedSession authenticated = login();
		MvcResult createResult = mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.session(authenticated.session())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "files": [
					    {"clientFileName":"first.jpg","mimeType":"image/jpeg","byteSize":1024},
					    {"clientFileName":"second.png","mimeType":"image/png","byteSize":2048}
					  ]
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("UPLOADING"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].uploadTicket.endpoint")
				.value("https://storage.example.test/upload/resumable/sign"))
			.andExpect(jsonPath("$.items[0].uploadTicket.token").value("signed-token"))
			.andReturn();

		String createBody = createResult.getResponse().getContentAsString();
		String batchId = JsonPath.read(createBody, "$.id");
		String firstItemId = JsonPath.read(createBody, "$.items[0].id");
		String secondItemId = JsonPath.read(createBody, "$.items[1].id");

		mockMvc.perform(post(itemPath(batchId, firstItemId, "progress"))
				.session(authenticated.session())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"uploadedBytes\":512}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.uploadedBytes").value(512));

		mockMvc.perform(post(itemPath(batchId, secondItemId, "failure"))
				.session(authenticated.session())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"failureCode\":\"NETWORK_ERROR\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));

		mockMvc.perform(post(itemPath(batchId, secondItemId, "retry"))
				.session(authenticated.session())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptCount").value(2))
			.andExpect(jsonPath("$.uploadTicket.token").value("signed-token"));

		mockMvc.perform(post(itemPath(batchId, firstItemId, "complete"))
				.session(authenticated.session())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PROCESSING"))
			.andExpect(jsonPath("$.uploadedBytes").value(1024));

		mockMvc.perform(get("/api/v1/manage/photo-uploads/{batchId}", batchId)
				.session(authenticated.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].uploadTicket").doesNotExist())
			.andExpect(jsonPath("$.items[0].status").value("PROCESSING"));
	}

	@Test
	void rejectsUnsupportedPhotoType() throws Exception {
		AuthenticatedSession authenticated = login();

		mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.session(authenticated.session())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"files":[{"clientFileName":"notes.txt","mimeType":"text/plain","byteSize":1024}]}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PHOTO_UPLOAD_UNSUPPORTED_TYPE"));
	}

	private AuthenticatedSession login() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andReturn();
		String csrfBody = csrfResult.getResponse().getContentAsString();
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		String headerName = JsonPath.read(csrfBody, "$.headerName");
		String token = JsonPath.read(csrfBody, "$.token");

		mockMvc.perform(post("/api/v1/auth/login")
				.session(session)
				.header(headerName, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"photo-admin@example.test","password":"local-photo-password"}
					"""))
			.andExpect(status().isOk());
		return new AuthenticatedSession(session, headerName, token);
	}

	private static String itemPath(String batchId, String itemId, String action) {
		return "/api/v1/manage/photo-uploads/" + batchId + "/items/" + itemId + "/" + action;
	}

	private record AuthenticatedSession(MockHttpSession session, String headerName, String token) {
	}
}
