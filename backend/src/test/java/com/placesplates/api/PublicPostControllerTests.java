package com.placesplates.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import com.placesplates.domain.auth.entity.AdministratorAccount;
import com.placesplates.domain.auth.repository.AdministratorAccountRepository;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;

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
	private PasswordEncoder passwordEncoder;

	private AdministratorAccount administrator;

	@BeforeEach
	void setUp() {
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
	void rejectsUnsupportedCategoryValue() throws Exception {
		mockMvc.perform(get("/api/v1/public/posts").queryParam("category", "ALL"))
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
}
