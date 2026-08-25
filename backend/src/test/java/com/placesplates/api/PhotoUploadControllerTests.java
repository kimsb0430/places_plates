package com.placesplates.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;

import com.jayway.jsonpath.JsonPath;
import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.repository.UploadBatchRepository;
import com.placesplates.domain.photo.repository.ImageProcessingJobRepository;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.photo.repository.UploadItemRepository;
import com.placesplates.domain.photo.service.ImageProcessingJobService;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.infra.storage.SignedUploadTicket;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.TemporaryUploadSigner;

import jakarta.servlet.http.Cookie;

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
	private ImageProcessingJobRepository imageProcessingJobRepository;

	@Autowired
	private UploadItemRepository uploadItemRepository;

	@Autowired
	private PhotoAssetRepository photoAssetRepository;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private ImageProcessingJobService imageProcessingJobService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DraftPostRepository draftPostRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private TemporaryUploadSigner uploadSigner;

	@MockitoBean
	private PrivatePhotoStorage privatePhotoStorage;

	@BeforeEach
	void setUp() {
		imageProcessingJobRepository.deleteAll();
		uploadBatchRepository.deleteAll();
		photoAssetRepository.deleteAll();
		photoRepository.deleteAll();
		draftPostRepository.deleteAll();
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
	void sanitizesCompletedUploadIntoPrivateMaster() throws Exception {
		byte[] source = createJpeg();
		when(privatePhotoStorage.downloadTemporary(anyString())).thenReturn(source);
		AuthenticatedSession authenticated = login();
		MvcResult createResult = mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"DESTINATION","files":[{"clientFileName":"private-name.jpg","mimeType":"image/jpeg","byteSize":%d}]}
					""".formatted(source.length)))
			.andExpect(status().isCreated())
			.andReturn();
		String body = createResult.getResponse().getContentAsString();
		String batchId = JsonPath.read(body, "$.id");
		String itemId = JsonPath.read(body, "$.items[0].id");

		mockMvc.perform(post(itemPath(batchId, itemId, "complete"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk());

		MvcResult sanitizeResult = mockMvc.perform(post(itemPath(batchId, itemId, "sanitize"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.photoId").isNotEmpty())
			.andExpect(jsonPath("$.failureCode").doesNotExist())
			.andExpect(jsonPath("$.variants.length()").value(3))
			.andExpect(jsonPath("$.variants[0].type").value("THUMBNAIL"))
			.andReturn();

		UUID photoId = UUID.fromString(JsonPath.read(
			sanitizeResult.getResponse().getContentAsString(),
			"$.photoId"
		));
		assertThat(uploadItemRepository.findById(UUID.fromString(itemId)).orElseThrow().getResultPhotoId())
			.isEqualTo(photoId);
		assertThat(photoRepository.count()).isEqualTo(1);
		assertThat(photoRepository.findById(photoId).orElseThrow().getProcessingStatus())
			.isEqualTo(PhotoProcessingStatus.READY);
		assertThat(photoAssetRepository.count()).isEqualTo(4);
		assertThat(photoAssetRepository.findAllByPhotoId(photoId))
			.filteredOn(asset -> asset.getVariantType().isResponsiveVariant())
			.allSatisfy(asset -> {
				assertThat(asset.getAccessLevel()).isEqualTo("PUBLIC");
				assertThat(asset.isMetadataScanPassed()).isTrue();
				assertThat(asset.isWatermarkApplied()).isTrue();
				assertThat(asset.getWatermarkVersion()).isEqualTo("places-plates-corner-v1");
				assertThat(asset.getWatermarkPosition()).isEqualTo("BOTTOM_RIGHT");
			});
		assertThat(imageProcessingJobRepository.findByUploadItemId(UUID.fromString(itemId)).orElseThrow().getStatus())
			.hasToString("COMPLETED");

		when(privatePhotoStorage.downloadSanitizedMaster(anyString())).thenReturn(source);
		jdbcTemplate.update(
			"""
			UPDATE photo_assets
			SET access_level = 'PRIVATE',
			    watermark_applied = FALSE,
			    watermark_version = NULL,
			    watermark_position = NULL
			WHERE photo_id = ?
			  AND variant_type <> 'SANITIZED_MASTER'
			""",
			photoId
		);
		mockMvc.perform(post(itemPath(batchId, itemId, "sanitize"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.variants.length()").value(3));
		assertThat(photoAssetRepository.findAllByPhotoId(photoId))
			.filteredOn(asset -> asset.getVariantType().isResponsiveVariant())
			.allSatisfy(asset -> {
				assertThat(asset.getAccessLevel()).isEqualTo("PUBLIC");
				assertThat(asset.isWatermarkApplied()).isTrue();
				assertThat(asset.getWatermarkVersion()).isEqualTo("places-plates-corner-v1");
				assertThat(asset.getWatermarkPosition()).isEqualTo("BOTTOM_RIGHT");
			});

		photoAssetRepository.deleteAll(photoAssetRepository.findAll().stream()
			.filter(asset -> asset.getVariantType().isResponsiveVariant())
			.toList());
		jdbcTemplate.update(
			"UPDATE photos SET processing_status = 'PROCESSING' WHERE id = ?",
			photoId
		);
		mockMvc.perform(post(itemPath(batchId, itemId, "sanitize"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.photoId").value(photoId.toString()))
			.andExpect(jsonPath("$.variants.length()").value(3));
		assertThat(photoRepository.findById(photoId).orElseThrow().getProcessingStatus())
			.isEqualTo(PhotoProcessingStatus.READY);
		assertThat(photoAssetRepository.findAllByPhotoId(photoId))
			.extracting(asset -> asset.getVariantType())
			.containsExactlyInAnyOrder(
				PhotoAssetVariantType.SANITIZED_MASTER,
				PhotoAssetVariantType.THUMBNAIL,
				PhotoAssetVariantType.MAP_CARD,
				PhotoAssetVariantType.PUBLIC_DETAIL
			);

		ArgumentCaptor<String> storageKey = ArgumentCaptor.forClass(String.class);
		verify(privatePhotoStorage).storeSanitizedMaster(
			storageKey.capture(),
			org.mockito.ArgumentMatchers.any(byte[].class),
			org.mockito.ArgumentMatchers.eq("image/jpeg")
		);
		assertThat(storageKey.getValue())
			.startsWith("sanitized/")
			.endsWith(".jpg")
			.doesNotContain("private-name");
		verify(privatePhotoStorage, org.mockito.Mockito.times(9)).storeResponsiveVariant(
			org.mockito.ArgumentMatchers.startsWith("variants/"),
			org.mockito.ArgumentMatchers.any(byte[].class),
			org.mockito.ArgumentMatchers.eq("image/jpeg")
		);
	}

	@Test
	void unauthenticatedUploadIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"RESTAURANT","files":[{"clientFileName":"photo.jpg","mimeType":"image/jpeg","byteSize":1024}]}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void createsDestinationDraftForLegacyUploadRequestWithoutCategory() throws Exception {
		AuthenticatedSession authenticated = login();

		mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"files":[{"clientFileName":"legacy.jpg","mimeType":"image/jpeg","byteSize":1024}]}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.draftPostId").isNotEmpty());

		mockMvc.perform(get("/api/v1/manage/drafts")
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].category").value("DESTINATION"));
	}

	@Test
	void createsTracksRetriesAndCompletesMultipleUploads() throws Exception {
		AuthenticatedSession authenticated = login();
		MvcResult createResult = mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "category": "RESTAURANT",
					  "files": [
					    {"clientFileName":"first.jpg","mimeType":"image/jpeg","byteSize":1024},
					    {"clientFileName":"second.png","mimeType":"image/png","byteSize":2048}
					  ]
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.draftPostId").isNotEmpty())
			.andExpect(jsonPath("$.status").value("UPLOADING"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].uploadTicket.endpoint")
				.value("https://storage.example.test/upload/resumable/sign"))
			.andExpect(jsonPath("$.items[0].uploadTicket.token").value("signed-token"))
			.andReturn();

		String createBody = createResult.getResponse().getContentAsString();
		String batchId = JsonPath.read(createBody, "$.id");
		String draftPostId = JsonPath.read(createBody, "$.draftPostId");
		String firstItemId = JsonPath.read(createBody, "$.items[0].id");
		String secondItemId = JsonPath.read(createBody, "$.items[1].id");

		mockMvc.perform(post(itemPath(batchId, firstItemId, "progress"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"uploadedBytes\":512}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.uploadedBytes").value(512));

		mockMvc.perform(post(itemPath(batchId, secondItemId, "failure"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"failureCode\":\"NETWORK_ERROR\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));

		mockMvc.perform(post(itemPath(batchId, secondItemId, "retry"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptCount").value(2))
			.andExpect(jsonPath("$.uploadTicket.token").value("signed-token"));

		mockMvc.perform(post(itemPath(batchId, firstItemId, "complete"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PROCESSING"))
			.andExpect(jsonPath("$.uploadedBytes").value(1024));

		mockMvc.perform(post(itemPath(batchId, firstItemId, "complete"))
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PROCESSING"));

		assertThat(imageProcessingJobRepository.count()).isEqualTo(1);
		var processingJob = imageProcessingJobRepository.findByUploadItemId(UUID.fromString(firstItemId))
			.orElseThrow();

		mockMvc.perform(get("/api/v1/manage/image-processing-jobs")
				.param("draftPostId", draftPostId)
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(processingJob.getId().toString()))
			.andExpect(jsonPath("$[0].draftPostId").value(draftPostId))
			.andExpect(jsonPath("$[0].uploadItemId").value(firstItemId))
			.andExpect(jsonPath("$[0].status").value("PENDING"))
			.andExpect(jsonPath("$[0].attemptCount").value(0))
			.andExpect(jsonPath("$[0].canRetry").value(false));

		var claimedJob = imageProcessingJobService.claimNextJob(processingJob.getOwnerUserId())
			.orElseThrow();
		imageProcessingJobService.failJob(
			processingJob.getOwnerUserId(),
			claimedJob.id(),
			"metadata decoder unavailable"
		);

		mockMvc.perform(post("/api/v1/manage/image-processing-jobs/{jobId}/retry", claimedJob.id())
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.attemptCount").value(1))
			.andExpect(jsonPath("$.lastFailureCode").value("METADATA_DECODER_UNAVAILABLE"));

		mockMvc.perform(get("/api/v1/manage/photo-uploads/{batchId}", batchId)
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].uploadTicket").doesNotExist())
			.andExpect(jsonPath("$.items[0].status").value("PROCESSING"));

		mockMvc.perform(get("/api/v1/manage/drafts")
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(draftPostId))
			.andExpect(jsonPath("$[0].category").value("RESTAURANT"))
			.andExpect(jsonPath("$[0].title").value("새 맛집 기록"))
			.andExpect(jsonPath("$[0].visibility").value("PRIVATE"))
			.andExpect(jsonPath("$[0].status").value("DRAFT"));

		mockMvc.perform(get("/api/v1/manage/drafts/{draftPostId}", draftPostId)
				.cookie(authenticated.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(draftPostId));
	}

	@Test
	void rejectsUnsupportedPhotoType() throws Exception {
		AuthenticatedSession authenticated = login();

		mockMvc.perform(post("/api/v1/manage/photo-uploads")
				.cookie(authenticated.cookie())
				.header(authenticated.headerName(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"DESTINATION","files":[{"clientFileName":"notes.txt","mimeType":"text/plain","byteSize":1024}]}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PHOTO_UPLOAD_UNSUPPORTED_TYPE"));
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
					{"email":"photo-admin@example.test","password":"local-photo-password"}
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

	private static String itemPath(String batchId, String itemId, String action) {
		return "/api/v1/manage/photo-uploads/" + batchId + "/items/" + itemId + "/" + action;
	}

	private static byte[] createJpeg() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB), "jpeg", output);
		return output.toByteArray();
	}

	private record AuthenticatedSession(Cookie cookie, String headerName, String token) {
	}
}
