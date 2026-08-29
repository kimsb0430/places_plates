package com.placesplates.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.placesplates.domain.post.entity.DestinationDetail;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.entity.RestaurantDetail;
import com.placesplates.domain.post.entity.RestaurantPriceRange;
import com.placesplates.domain.post.entity.RevisitIntention;
import com.placesplates.domain.post.repository.DestinationDetailRepository;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.post.repository.RestaurantDetailRepository;
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
	private RestaurantDetailRepository restaurantDetailRepository;

	@Autowired
	private DestinationDetailRepository destinationDetailRepository;

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
		restaurantDetailRepository.deleteAll();
		destinationDetailRepository.deleteAll();
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
	void publicJsonNeverExposesOwnerOrPrivateStorageCoordinates() throws Exception {
		DraftPost post = publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "저장 경로 비공개 식당");
		attachSafeCover(post, "저장 경로가 숨겨진 대표 사진");
		attachSafeDetailPhoto(post, "저장 경로가 숨겨진 상세 사진", 1, false);

		mockMvc.perform(get("/api/v1/public/posts"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("ownerUserId"))))
			.andExpect(content().string(not(containsString("storageKey"))))
			.andExpect(content().string(not(containsString("temporaryStorageKey"))))
			.andExpect(content().string(not(containsString("variants/"))))
			.andExpect(content().string(not(containsString("temporary/"))));

		mockMvc.perform(get("/api/v1/public/posts/{postId}", post.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("ownerUserId"))))
			.andExpect(content().string(not(containsString("storageKey"))))
			.andExpect(content().string(not(containsString("temporaryStorageKey"))))
			.andExpect(content().string(not(containsString("variants/"))))
			.andExpect(content().string(not(containsString("temporary/"))));
	}

	@Test
	void hidesEveryNonPublicStateFromListsDetailsPhotosAndPlaceHistory() throws Exception {
		DraftPost publicRestaurant = publishedPost(
			PostCategory.RESTAURANT,
			PostVisibility.PUBLIC,
			"모두에게 보이는 식당"
		);
		DraftPost publicDestination = publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			"모두에게 보이는 여행지"
		);
		DraftPost unlisted = publishedPost(
			PostCategory.RESTAURANT,
			PostVisibility.UNLISTED,
			"링크로만 보는 식당"
		);
		DraftPost privatePost = publishedPost(
			PostCategory.DESTINATION,
			PostVisibility.PRIVATE,
			"소유자만 보는 여행지"
		);
		DraftPost draft = draftPost(PostCategory.RESTAURANT, "작성 중인 식당");
		PhotoAsset unlistedPhoto = attachSafeDetailPhoto(unlisted, "링크 공개 사진", 1, false);
		PhotoAsset privatePhoto = attachSafeDetailPhoto(privatePost, "비공개 사진", 1, false);
		PhotoAsset draftPhoto = attachSafeDetailPhoto(draft, "초안 사진", 1, false);
		attachSafeCover(unlisted, "링크 공개 대표 사진");
		attachSafeCover(privatePost, "비공개 대표 사진");
		attachSafeCover(draft, "초안 대표 사진");

		mockMvc.perform(get("/api/v1/public/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.counts.restaurant").value(1))
			.andExpect(jsonPath("$.counts.destination").value(1))
			.andExpect(jsonPath("$.posts.length()").value(2))
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(publicRestaurant.getId())).exists())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(publicDestination.getId())).exists())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(unlisted.getId())).doesNotExist())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(privatePost.getId())).doesNotExist())
			.andExpect(jsonPath("$.posts[?(@.id == '%s')]".formatted(draft.getId())).doesNotExist());

		mockMvc.perform(get("/api/v1/public/posts").queryParam("category", "RESTAURANT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].id").value(publicRestaurant.getId().toString()));
		mockMvc.perform(get("/api/v1/public/posts").queryParam("category", "DESTINATION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.all").value(2))
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].id").value(publicDestination.getId().toString()));

		assertHiddenFromEveryPublicReadPath(unlisted, unlistedPhoto.getPhotoId());
		assertHiddenFromEveryPublicReadPath(privatePost, privatePhoto.getPhotoId());
		assertHiddenFromEveryPublicReadPath(draft, draftPhoto.getPhotoId());
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
			.andExpect(header().string("Cache-Control", containsString("stale-while-revalidate=86400")))
			.andExpect(header().string("Content-Disposition", "inline; filename=\"places-plates-cover.jpg\""))
			.andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
			.andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void returnsRestaurantPostDetailsAndOnlySafeDetailPhotosWithoutLogin() throws Exception {
		DraftPost post = publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "골목의 저녁 식탁");
		RestaurantDetail details = RestaurantDetail.create(post.getId());
		details.update(
			new BigDecimal("4.5"),
			"제철 생선구이",
			RestaurantPriceRange.MODERATE,
			15,
			RevisitIntention.YES
		);
		restaurantDetailRepository.save(details);
		attachSafeDetailPhoto(post, "식탁 전체가 보이는 사진", 0, true);
		attachSafeDetailPhoto(post, "생선구이 한 접시", 1, false);

		mockMvc.perform(get("/api/v1/public/posts/{postId}", post.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(post.getId().toString()))
			.andExpect(jsonPath("$.category").value("RESTAURANT"))
			.andExpect(jsonPath("$.title").value("골목의 저녁 식탁"))
			.andExpect(jsonPath("$.summary").value("골목의 저녁 식탁의 한줄평"))
			.andExpect(jsonPath("$.content").value("공개 상세 이전의 비공개 본문"))
			.andExpect(jsonPath("$.publicVisitYear").value(2026))
			.andExpect(jsonPath("$.publicVisitMonth").value(8))
			.andExpect(jsonPath("$.place.name").value("골목의 저녁 식탁 장소"))
			.andExpect(jsonPath("$.restaurantDetails.rating").value(4.5))
			.andExpect(jsonPath("$.restaurantDetails.recommendedMenu").value("제철 생선구이"))
			.andExpect(jsonPath("$.restaurantDetails.priceRange").value("MODERATE"))
			.andExpect(jsonPath("$.restaurantDetails.waitingMinutes").value(15))
			.andExpect(jsonPath("$.restaurantDetails.revisitIntention").value("YES"))
			.andExpect(jsonPath("$.destinationDetails").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.photos.length()").value(2))
			.andExpect(jsonPath("$.photos[0].cover").value(true))
			.andExpect(jsonPath("$.photos[0].path").value(containsString("/photos/")))
			.andExpect(jsonPath("$.photos[1].altText").value("생선구이 한 접시"))
			.andExpect(jsonPath("$.ownerUserId").doesNotExist())
			.andExpect(jsonPath("$.place.id").doesNotExist())
			.andExpect(jsonPath("$.place.latitude").doesNotExist())
			.andExpect(jsonPath("$.publishedAt").doesNotExist())
			.andExpect(jsonPath("$.visibility").doesNotExist());
	}

	@Test
	void returnsDestinationSpecificDetailsWithoutRestaurantFields() throws Exception {
		DraftPost post = publishedPost(PostCategory.DESTINATION, PostVisibility.PUBLIC, "비 오는 숲길");
		DestinationDetail details = DestinationDetail.create(post.getId());
		details.update("이른 아침", 120, "안개 낀 산책로", "미끄럽지 않은 신발을 준비하세요.");
		destinationDetailRepository.save(details);

		mockMvc.perform(get("/api/v1/public/posts/{postId}", post.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.category").value("DESTINATION"))
			.andExpect(jsonPath("$.restaurantDetails").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.destinationDetails.recommendedTime").value("이른 아침"))
			.andExpect(jsonPath("$.destinationDetails.durationMinutes").value(120))
			.andExpect(jsonPath("$.destinationDetails.highlights").value("안개 낀 산책로"))
			.andExpect(jsonPath("$.destinationDetails.travelTips").value("미끄럽지 않은 신발을 준비하세요."));
	}

	@Test
	void returnsChronologicalPublicVisitsForTheSamePlace() throws Exception {
		Place sharedPlace = placeRepository.save(Place.manual(
			administrator.getId(),
			"계절마다 다시 찾는 정원",
			"공개 장소 방문 기록 테스트 주소",
			null,
			null,
			"https://www.google.com/maps/search/?api=1&query=garden"
		));
		DraftPost firstVisit = publishedPostAtPlace(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			"봄의 정원",
			sharedPlace,
			2025,
			4
		);
		DraftPost latestVisit = publishedPostAtPlace(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			"가을의 정원",
			sharedPlace,
			2026,
			10
		);
		DraftPost summerVisit = publishedPostAtPlace(
			PostCategory.DESTINATION,
			PostVisibility.PUBLIC,
			"여름의 정원",
			sharedPlace,
			2026,
			7
		);
		AdministratorAccount anotherOwner = accountRepository.save(AdministratorAccount.create(
			"other-place-owner-" + UUID.randomUUID() + "@example.test",
			passwordEncoder.encode("other-local-public-post-password")
		));
		DraftPost otherOwnersVisit = DraftPost.create(anotherOwner.getId(), PostCategory.DESTINATION);
		otherOwnersVisit.updateEditorFields(
			"다른 회원의 겨울 정원",
			"다른 회원에게만 속한 방문",
			null,
			2026,
			12
		);
		otherOwnersVisit.connectPlace(sharedPlace.getId());
		otherOwnersVisit.publish(PostVisibility.PUBLIC);
		draftPostRepository.save(otherOwnersVisit);
		publishedPostAtPlace(
			PostCategory.DESTINATION,
			PostVisibility.UNLISTED,
			"링크로만 보는 늦여름 정원",
			sharedPlace,
			2026,
			8
		);
		attachSafeCover(firstVisit, "봄 정원의 대표 사진");

		mockMvc.perform(get("/api/v1/public/posts/{postId}/place", latestVisit.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.place.name").value("계절마다 다시 찾는 정원"))
			.andExpect(jsonPath("$.place.googleMapsUrl").value(containsString("google.com/maps")))
			.andExpect(jsonPath("$.place.id").doesNotExist())
			.andExpect(jsonPath("$.place.latitude").doesNotExist())
			.andExpect(jsonPath("$.visitCount").value(3))
			.andExpect(jsonPath("$.visits.length()").value(3))
			.andExpect(jsonPath("$.visits[0].id").value(firstVisit.getId().toString()))
			.andExpect(jsonPath("$.visits[0].publicVisitYear").value(2025))
			.andExpect(jsonPath("$.visits[0].cover.path").value(
				"/api/v1/public/posts/%s/cover".formatted(firstVisit.getId())
			))
			.andExpect(jsonPath("$.visits[1].id").value(summerVisit.getId().toString()))
			.andExpect(jsonPath("$.visits[1].publicVisitMonth").value(7))
			.andExpect(jsonPath("$.visits[2].id").value(latestVisit.getId().toString()))
			.andExpect(jsonPath("$.visits[2].publishedAt").doesNotExist())
			.andExpect(jsonPath("$.visits[?(@.title == '링크로만 보는 늦여름 정원')]").doesNotExist())
			.andExpect(jsonPath("$.visits[?(@.title == '다른 회원의 겨울 정원')]").doesNotExist());
	}

	@Test
	void hidesPlaceHistoryWhenTheAnchorPostIsNotPublic() throws Exception {
		DraftPost unlisted = publishedPost(PostCategory.RESTAURANT, PostVisibility.UNLISTED, "숨은 장소 기록");

		mockMvc.perform(get("/api/v1/public/posts/{postId}/place", unlisted.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_PLACE_HISTORY_NOT_FOUND"));
	}

	@Test
	void hidesNonPublicPostsFromThePublicDetailEndpoint() throws Exception {
		DraftPost unlisted = publishedPost(PostCategory.RESTAURANT, PostVisibility.UNLISTED, "링크 공개 식당");
		DraftPost privatePost = publishedPost(PostCategory.DESTINATION, PostVisibility.PRIVATE, "비공개 여행지");

		mockMvc.perform(get("/api/v1/public/posts/{postId}", unlisted.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/public/posts/{postId}", privatePost.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_NOT_FOUND"));
	}

	@Test
	void streamsOnlyTheSafeWatermarkedPublicDetailPhoto() throws Exception {
		byte[] imageBytes = new byte[] { 5, 6, 7, 8 };
		DraftPost post = publishedPost(PostCategory.DESTINATION, PostVisibility.PUBLIC, "워터마크 상세 여행지");
		PhotoAsset asset = attachSafeDetailPhoto(post, "상세 풍경", 0, true);
		when(privatePhotoStorage.downloadResponsiveVariant(asset.getStorageKey())).thenReturn(imageBytes);

		mockMvc.perform(get(
			"/api/v1/public/posts/{postId}/photos/{photoId}",
			post.getId(),
			asset.getPhotoId()
		))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andExpect(content().bytes(imageBytes))
			.andExpect(header().string("Cache-Control", containsString("max-age=3600")))
			.andExpect(header().string("Cache-Control", containsString("stale-while-revalidate=86400")))
			.andExpect(header().string("Content-Disposition", "inline; filename=\"places-plates-photo.jpg\""))
			.andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
			.andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void hidesPrivateResponsiveAssetsFromThePublicDetailEndpoint() throws Exception {
		DraftPost post = publishedPost(PostCategory.RESTAURANT, PostVisibility.PUBLIC, "비공개 상세 파생본");
		Photo photo = readyCover(post, "아직 공개할 수 없는 상세 사진");
		photoAssetRepository.save(PhotoAsset.privateResponsiveVariant(
			photo.getId(),
			PhotoAssetVariantType.PUBLIC_DETAIL,
			"variants/%s/public-detail-private.jpg".formatted(photo.getId()),
			"image/jpeg",
			1600,
			1200,
			8192
		));

		mockMvc.perform(get("/api/v1/public/posts/{postId}", post.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photos.length()").value(0));
		mockMvc.perform(get(
			"/api/v1/public/posts/{postId}/photos/{photoId}",
			post.getId(),
			photo.getId()
		))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_PHOTO_NOT_FOUND"));
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
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_QUERY"));
	}

	@Test
	void rejectsUnsupportedSortValue() throws Exception {
		mockMvc.perform(get("/api/v1/public/posts").queryParam("sort", "POPULAR"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_QUERY"));
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
		return publishedPostAtPlace(category, visibility, title, place, 2026, 8);
	}

	private DraftPost draftPost(PostCategory category, String title) {
		Place place = placeRepository.save(Place.manual(
			administrator.getId(),
			title + " 장소",
			"공개 범위 회귀 테스트 주소",
			null,
			null,
			"https://www.google.com/maps/search/?api=1&query=visibility"
		));
		DraftPost post = DraftPost.create(administrator.getId(), category);
		post.updateEditorFields(title, title + "의 한줄평", "공개되면 안 되는 초안 본문", 2026, 8);
		post.connectPlace(place.getId());
		return draftPostRepository.save(post);
	}

	private DraftPost publishedPostAtPlace(
		PostCategory category,
		PostVisibility visibility,
		String title,
		Place place,
		int visitYear,
		int visitMonth
	) {
		DraftPost post = DraftPost.create(administrator.getId(), category);
		post.updateEditorFields(
			title,
			title + "의 한줄평",
			"공개 상세 이전의 비공개 본문",
			visitYear,
			visitMonth
		);
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

	private PhotoAsset attachSafeDetailPhoto(
		DraftPost post,
		String altText,
		int displayOrder,
		boolean cover
	) {
		Photo photo = readyPhoto(post, altText, displayOrder, cover);
		return photoAssetRepository.save(PhotoAsset.publicWatermarkedVariant(
			photo.getId(),
			PhotoAssetVariantType.PUBLIC_DETAIL,
			"variants/%s/public-detail.jpg".formatted(photo.getId()),
			"image/jpeg",
			1600,
			1200,
			8192,
			"places-plates-corner-v1",
			"BOTTOM_RIGHT"
		));
	}

	private Photo readyCover(DraftPost post, String altText) {
		return readyPhoto(post, altText, 0, true);
	}

	private Photo readyPhoto(DraftPost post, String altText, int displayOrder, boolean cover) {
		Photo photo = Photo.processing(administrator.getId(), post.getId());
		photo.markReady();
		photo.updateEditorState(displayOrder, cover, altText);
		return photoRepository.save(photo);
	}

	/**
	 * 非公開状態の投稿について、すべての公開読取経路が同じ404境界を維持することを確認する。
	 */
	private void assertHiddenFromEveryPublicReadPath(DraftPost post, UUID photoId) throws Exception {
		mockMvc.perform(get("/api/v1/public/posts/{postId}", post.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/public/posts/{postId}/cover", post.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_COVER_NOT_FOUND"));
		mockMvc.perform(get(
			"/api/v1/public/posts/{postId}/photos/{photoId}",
			post.getId(),
			photoId
		))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_POST_PHOTO_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/public/posts/{postId}/place", post.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUBLIC_PLACE_HISTORY_NOT_FOUND"));
	}
}
