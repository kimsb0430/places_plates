package com.placesplates.domain.map.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.map.dto.MapPostCountsResponse;
import com.placesplates.domain.map.dto.MapPostListResponse;
import com.placesplates.domain.map.dto.MapPostMarkerResponse;
import com.placesplates.domain.place.entity.Place;
import com.placesplates.domain.place.entity.PlaceSource;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostCoordinateVisibility;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;

@Service
@Transactional(readOnly = true)
public class PublicMapService {

	private static final int GOOGLE_COORDINATE_CACHE_DAYS = 30;

	private final DraftPostRepository draftPostRepository;
	private final PlaceRepository placeRepository;

	public PublicMapService(
		DraftPostRepository draftPostRepository,
		PlaceRepository placeRepository
	) {
		this.draftPostRepository = draftPostRepository;
		this.placeRepository = placeRepository;
	}

	public MapPostListResponse findMapPosts(PostCategory category) {
		List<DraftPost> publicPosts = draftPostRepository.findAllByVisibilityAndStatus(
			PostVisibility.PUBLIC,
			PostStatus.PUBLISHED,
			Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id"))
		);
		Map<UUID, Place> places = placeRepository.findAllById(
			publicPosts.stream()
				.map(DraftPost::getPlaceId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList()
		).stream().collect(Collectors.toMap(Place::getId, Function.identity()));
		OffsetDateTime freshnessThreshold = OffsetDateTime.now(ZoneOffset.UTC)
			.minusDays(GOOGLE_COORDINATE_CACHE_DAYS);
		List<DraftPost> mappablePosts = publicPosts.stream()
			.filter(post -> isMappable(post, places.get(post.getPlaceId()), freshnessThreshold))
			.toList();
		long restaurantCount = mappablePosts.stream()
			.filter(post -> post.getCategory() == PostCategory.RESTAURANT)
			.count();
		long destinationCount = mappablePosts.size() - restaurantCount;
		List<MapPostMarkerResponse> markers = mappablePosts.stream()
			.filter(post -> category == null || post.getCategory() == category)
			.map(post -> MapPostMarkerResponse.from(post, places.get(post.getPlaceId())))
			.toList();
		return new MapPostListResponse(
			new MapPostCountsResponse(
				restaurantCount + destinationCount,
				restaurantCount,
				destinationCount
			),
			markers
		);
	}

	/**
	 * 公開が許可された座標と有効期間内のGoogleスナップショットだけを地図対象にする。
	 */
	private static boolean isMappable(
		DraftPost post,
		Place place,
		OffsetDateTime freshnessThreshold
	) {
		if (place == null
			|| place.getLatitude() == null
			|| place.getLongitude() == null
			|| post.getCoordinateVisibility() == PostCoordinateVisibility.HIDDEN) {
			return false;
		}
		return place.getSource() != PlaceSource.GOOGLE
			|| (place.getRefreshedAt() != null && !place.getRefreshedAt().isBefore(freshnessThreshold));
	}
}
