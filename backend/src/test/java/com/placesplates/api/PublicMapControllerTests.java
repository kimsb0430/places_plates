package com.placesplates.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostCoordinateVisibility;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:public-map;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class PublicMapControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdministratorAccountRepository accountRepository;

	@Autowired
	private DraftPostRepository draftPostRepository;

	@Autowired
	private PlaceRepository placeRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private AdministratorAccount administrator;

	@BeforeEach
	void setUp() {
		draftPostRepository.deleteAll();
		placeRepository.deleteAll();
		accountRepository.deleteAll();
		administrator = accountRepository.save(AdministratorAccount.create(
			"public-map-" + UUID.randomUUID() + "@example.test",
			passwordEncoder.encode("local-public-map-password")
		));
	}

	@Test
	void listsOnlyMappablePublishedPublicPostsAndCountsCategoriesWithoutLogin() throws Exception {
		DraftPost restaurant = publishedPost(
			PostCategory.RESTAURANT,
			PostVisibility.PUBLIC,
			manualPlace("골목 식당", "37.566500", "126.978000"),
			PostCoordinateVisibility.EXACT
		);
		DraftPost destination = publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			googlePlace("강변 산책로", "35.681236", "139.767125"),
			PostCoordinateVisibility.APPROXIMATE
		);
		publishedPost(
			PostCategory.RESTAURANT,
			PostVisibility.PUBLIC,
			manualPlace("좌표 비공개 식당", "35.000000", "135.000000"),
			PostCoordinateVisibility.HIDDEN
		);
		publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.UNLISTED,
			manualPlace("링크 공개 여행지", "34.000000", "134.000000"),
			PostCoordinateVisibility.EXACT
		);
		publishedPost(
			PostCategory.RESTAURANT,
			PostVisibility.PRIVATE,
			manualPlace("비공개 식당", "33.000000", "133.000000"),
			PostCoordinateVisibility.EXACT
		);
		DraftPost draft = DraftPost.create(administrator.getId(), PostCategory.DESTINATION);
		Place draftPlace = manualPlace("작성 중인 여행지", "32.000000", "132.000000");
		draft.updateEditorFields("작성 중인 여행지 기록", "초안", "초안 본문", 2026, 8);
		draft.connectPlace(draftPlace.getId(), PostCoordinateVisibility.EXACT);
		draftPostRepository.save(draft);

		mockMvc.perform(get("/api/v1/map/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.counts.restaurant").value(1))
			.andExpect(jsonPath("$.counts.destination").value(1))
			.andExpect(jsonPath("$.posts.length()").value(2))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].latitude".formatted(restaurant.getId()))
				.value(37.566500))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].longitude".formatted(restaurant.getId()))
				.value(126.978000))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].latitude".formatted(destination.getId()))
				.value(35.68))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].longitude".formatted(destination.getId()))
				.value(139.77))
			.andExpect(jsonPath("$.posts[0].ownerUserId").doesNotExist())
			.andExpect(jsonPath("$.posts[0].placeId").doesNotExist())
			.andExpect(jsonPath("$.posts[0].coordinateVisibility").doesNotExist())
			.andExpect(jsonPath("$.posts[0].googleMapsUrl").doesNotExist())
			.andExpect(jsonPath("$.posts[0].refreshedAt").doesNotExist());
	}

	@Test
	void categoryFilterMatchesReturnedMarkerCategoryWithoutChangingGlobalCounts() throws Exception {
		publishedPost(
			PostCategory.RESTAURANT,
			PostVisibility.PUBLIC,
			manualPlace("맛집", "37.500000", "127.000000"),
			PostCoordinateVisibility.EXACT
		);
		publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			manualPlace("여행지", "35.500000", "129.000000"),
			PostCoordinateVisibility.EXACT
		);

		mockMvc.perform(get("/api/v1/map/posts").queryParam("category", "RESTAURANT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].category").value("RESTAURANT"));

		mockMvc.perform(get("/api/v1/map/posts").queryParam("category", "DESTINATION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].category").value("DESTINATION"));
	}

	@Test
	void countsRepeatVisitsAtTheSamePlaceAsSeparateMapPosts() throws Exception {
		Place sharedPlace = manualPlace("계절마다 다시 찾는 공원", "37.551200", "126.988200");
		DraftPost springVisit = publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			sharedPlace,
			PostCoordinateVisibility.EXACT
		);
		DraftPost autumnVisit = publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			sharedPlace,
			PostCoordinateVisibility.EXACT
		);

		mockMvc.perform(get("/api/v1/map/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.counts.restaurant").value(0))
			.andExpect(jsonPath("$.counts.destination").value(2))
			.andExpect(jsonPath("$.posts.length()").value(2))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(springVisit.getId())).exists())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(autumnVisit.getId())).exists())
			.andExpect(jsonPath("$.posts[0].latitude").value(37.551200))
			.andExpect(jsonPath("$.posts[1].latitude").value(37.551200));
	}

	@Test
	void excludesExpiredGoogleCoordinateSnapshots() throws Exception {
		Place stalePlace = googlePlace("오래된 Google 장소", "35.005000", "135.764000");
		DraftPost stalePost = publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			stalePlace,
			PostCoordinateVisibility.EXACT
		);
		jdbcTemplate.update(
			"UPDATE places SET refreshed_at = ? WHERE id = ?",
			OffsetDateTime.now(ZoneOffset.UTC).minusDays(31),
			stalePlace.getId()
		);

		mockMvc.perform(get("/api/v1/map/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(0))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(stalePost.getId())).doesNotExist());
	}

	@Test
	void rejectsUnsupportedMapCategory() throws Exception {
		mockMvc.perform(get("/api/v1/map/posts").queryParam("category", "ALL"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_QUERY"));
	}

	private Place manualPlace(String name, String latitude, String longitude) {
		return placeRepository.save(Place.manual(
			administrator.getId(),
			name,
			"지도 테스트 주소",
			new BigDecimal(latitude),
			new BigDecimal(longitude),
			null
		));
	}

	private Place googlePlace(String name, String latitude, String longitude) {
		return placeRepository.save(Place.google(
			administrator.getId(),
			"google-" + UUID.randomUUID(),
			name,
			"point_of_interest",
			"Google 지도 테스트 주소",
			new BigDecimal(latitude),
			new BigDecimal(longitude),
			null
		));
	}

	private DraftPost publishedPost(
		PostCategory category,
		PostVisibility visibility,
		Place place,
		PostCoordinateVisibility coordinateVisibility
	) {
		DraftPost post = DraftPost.create(administrator.getId(), category);
		post.updateEditorFields(
			place.getName() + " 기록",
			place.getName() + " 한줄평",
			"지도 테스트 본문",
			2026,
			8
		);
		post.connectPlace(place.getId(), coordinateVisibility);
		post.publish(visibility);
		return draftPostRepository.save(post);
	}
}
