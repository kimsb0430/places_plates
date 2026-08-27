package com.placesplates.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.infra.storage.PrivatePhotoStorage;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:public-posts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class PublicPostControllerTests {

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
	private PasswordEncoder passwordEncoder;

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
			"public-posts-" + UUID.randomUUID() + "@example.test",
			passwordEncoder.encode("local-public-post-password")
		));
	}

	@Test
	void listsOnlyPublishedPublicPostsAndReturnsCategoryCountsWithoutLogin() throws Exception {
		DraftPost restaurant = publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "골목의 작은 식당");
		DraftPost destination = publishedPost(PostCategory.DESTINATION, PostVisibility.PUBLIC, "바다가 보이는 산책길");
		attachSafeCover(restaurant, "따뜻한 식탁의 대표 사진");
		publishedPost(PostCategory.RESTAURANT, PostVisibility.UNLISTED, "링크로만 보는 식당");
		publishedPost(PostCategory.DESTINATION, PostVisibility.PRIVATE, "비공개 여행지");
		draftPostRepository.save(DraftPost.create(administrator.getId(), PostCategory.RESTAURANT));

		mockMvc.perform(get("/api/v1/public/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.counts.restaurant").value(1))
			.andExpect(jsonPath("$.counts.destination").value(1))
			.andExpect(jsonPath("$.posts.length()").value(2))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(restaurant.getId())).exists())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(destination.getId())).exists())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].cover.path".formatted(restaurant.getId()))
				.value("/api/v1/public/posts/%s/cover".formatted(restaurant.getId())))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].cover.altText".formatted(restaurant.getId()))
				.value("따뜻한 식탁의 대표 사진"))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')].cover".formatted(destination.getId()))
				.value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
			.andExpect(jsonPath("$.posts[0].ownerUserId").doesNotExist())
			.andExpect(jsonPath("$.posts[0].content").doesNotExist())
			.andExpect(jsonPath("$.posts[0].placeId").doesNotExist());
	}

	@Test
	void filtersPostsWithoutChangingGlobalCategoryCounts() throws Exception {
		publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "첫 번째 맛집");
		publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "두 번째 맛집");
		publishedPost(PostCategory.DESTINATION, PostVisibility.PUBLIC, "한 곳의 여행지");

		mockMvc.perform(get("/api/v1/public/posts").queryParam("category", "RESTAURANT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(3))
			.andExpect(jsonPath("$.counts.restaurant").value(2))
			.andExpect(jsonPath("$.counts.destination").value(1))
			.andExpect(jsonPath("$.posts.length()").value(2))
			.andExpect(jsonPath("$.posts[0].category").value("RESTAURANT"))
			.andExpect(jsonPath("$.posts[1].category").value("RESTAURANT"));
	}

	@Test
	void sortsPublicPostsByPublishedTime() throws Exception {
		DraftPost older = publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "먼저 공개한 기록");
		Thread.sleep(10);
		DraftPost latest = publishedPost(PostCategory.DESTINATION, PostVisibility.PUBLIC, "나중에 공개한 기록");

		mockMvc.perform(get("/api/v1/public/posts").queryParam("sort", "LATEST"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].id").value(latest.getId().toString()))
			.andExpect(jsonPath("$.posts[1].id").value(older.getId().toString()));

		mockMvc.perform(get("/api/v1/public/posts").queryParam("sort", "OLDEST"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].id").value(older.getId().toString()))
			.andExpect(jsonPath("$.posts[1].id").value(latest.getId().toString()));
	}

	@Test
	void streamsOnlyTheSafeWatermarkedMapCardWithoutLogin() throws Exception {
		byte[] imageBytes = new byte[] { 1, 2, 3, 4 };
		DraftPost post = publishedPost(PostCategory.DESTINATION, PostVisibility.PUBLIC, "워터마크 여행지");
		PhotoAsset asset = attachSafeCover(post, "워터마크가 적용된 풍경");
		when(privatePhotoStorage.downloadResponsiveVariant(asset.getStorageKey())).thenReturn(imageBytes);

		mockMvc.perform(get("/api/v1/public/posts/{postId}/cover", post.getId()))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(content().bytes(imageBytes))
			.andExpect(header().string("Cache-Control", containsString("max-age=3600")))
			.andExpect(header().string("Content-Disposition", "inline; filename=\"places-plates-cover.jpg\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void hidesPrivateResponsiveAssetsFromThePublicCoverEndpoint() throws Exception {
		DraftPost post = publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "검사 전 대표 사진");
		Photo photo = readyCover(post, "검사 전 사진");
		photoAssetRepository.save(PhotoAsset.privateResponsiveVariant(
			photo.getId(),
			PhotoAssetVariantType.MAP_CARD,
			"variants/%s/map-card-private.jpg".formatted(photo.getId()),
			"image/jpeg",
			960,
			640,
			4096
		));

		mockMvc.perform(get("/api/v1/public/posts/{postId}/cover", post.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_COVER_NOT_FOUND"));
	}

	@Test
	void rejectsUnsupportedCategoryValue() throws Exception {
		mockMvc.perform(get("/api/v1/public/posts").queryParam("category", "ALL"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsUnsupportedSortValue() throws Exception {
		mockMvc.perform(get("/api/v1/public/posts").queryParam("sort", "POPULAR"))
			.andExpect(status().isBadRequest());
	}

	private DraftPost publishedPost(
		PostCategory category,
		PostVisibility visibility,
		String title
	) {
		Place place = placeRepository.save(Place.manual(
			administrator.getId(),
			title + " 장소",
			"공개 목록 테스트 주소",
			null,
			null,
			"https://www.google.com/maps/search/?api=1&query=test"
		));
		DraftPost post = DraftPost.create(administrator.getId(), category);
		post.updateEditorFields(title, title + "의 한줄평", "공개 상세 이전의 비공개 본문", 2026, 8);
		post.connectPlace(place.getId());
		post.publish(visibility);
		return draftPostRepository.save(post);
	}

	private PhotoAsset attachSafeCover(DraftPost post, String altText) {
		Photo photo = readyCover(post, altText);
		return photoAssetRepository.save(PhotoAsset.publicWatermarkedVariant(
			photo.getId(),
			PhotoAssetVariantType.MAP_CARD,
			"variants/%s/map-card.jpg".formatted(photo.getId()),
			"image/jpeg",
			960,
			640,
			4096,
			"places-plates-corner-v1",
			"BOTTOM_RIGHT"
		));
	}

	private Photo readyCover(DraftPost post, String altText) {
		Photo photo = Photo.processing(administrator.getId(), post.getId());
		photo.markReady();
		photo.updateEditorState(0, true, altText);
		return photoRepository.save(photo);
	}
}
