package com.placesplates.domain.post.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.photo.entity.Photo;
import com.placesplates.domain.photo.entity.PhotoAsset;
import com.placesplates.domain.photo.entity.PhotoAssetVariantType;
import com.placesplates.domain.photo.entity.PhotoProcessingStatus;
import com.placesplates.domain.photo.repository.PhotoAssetRepository;
import com.placesplates.domain.photo.repository.PhotoRepository;
import com.placesplates.domain.place.repository.PlaceRepository;
import com.placesplates.domain.post.dto.PublicPostCoverContent;
import com.placesplates.domain.post.dto.PublicPostCoverResponse;
import com.placesplates.domain.post.dto.PublicPostCountsResponse;
import com.placesplates.domain.post.dto.PublicPostDetailResponse;
import com.placesplates.domain.post.dto.PublicPostListResponse;
import com.placesplates.domain.post.dto.PublicPostPhotoContent;
import com.placesplates.domain.post.dto.PublicPostPhotoResponse;
import com.placesplates.domain.post.dto.PublicPostPlaceResponse;
import com.placesplates.domain.post.dto.PublicPostSort;
import com.placesplates.domain.post.dto.PublicPostSummaryResponse;
import com.placesplates.domain.post.dto.RestaurantDetailResponse;
import com.placesplates.domain.post.dto.DestinationDetailResponse;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DestinationDetailRepository;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.post.repository.PostCategoryCount;
import com.placesplates.domain.post.repository.RestaurantDetailRepository;
import com.placesplates.domain.post.exception.PublicPostException;
import com.placesplates.infra.storage.PrivatePhotoStorage;
import com.placesplates.infra.storage.StorageAccessException;

@Service
@Transactional(readOnly = true)
public class PublicPostService {

	private static final String PUBLIC_ACCESS = "PUBLIC";
	private static final String WATERMARK_POSITION = "BOTTOM_RIGHT";

	private final DraftPostRepository draftPostRepository;
	private final RestaurantDetailRepository restaurantDetailRepository;
	private final DestinationDetailRepository destinationDetailRepository;
	private final PlaceRepository placeRepository;
	private final PhotoRepository photoRepository;
	private final PhotoAssetRepository photoAssetRepository;
	private final PrivatePhotoStorage privatePhotoStorage;
	private final String watermarkVersion;

	public PublicPostService(
		DraftPostRepository draftPostRepository,
		RestaurantDetailRepository restaurantDetailRepository,
		DestinationDetailRepository destinationDetailRepository,
		PlaceRepository placeRepository,
		PhotoRepository photoRepository,
		PhotoAssetRepository photoAssetRepository,
		PrivatePhotoStorage privatePhotoStorage,
		@Value("${places-plates.image.watermark.version:places-plates-corner-v1}") String watermarkVersion
	) {
		this.draftPostRepository = draftPostRepository;
		this.restaurantDetailRepository = restaurantDetailRepository;
		this.destinationDetailRepository = destinationDetailRepository;
		this.placeRepository = placeRepository;
		this.photoRepository = photoRepository;
		this.photoAssetRepository = photoAssetRepository;
		this.privatePhotoStorage = privatePhotoStorage;
		this.watermarkVersion = watermarkVersion;
	}

	public PublicPostListResponse findPublicPosts(PostCategory category, PublicPostSort sort) {
		Sort databaseSort = publishedAtSort(sort);
		List<DraftPost> posts = category == null
			? draftPostRepository.findAllByVisibilityAndStatus(
				PostVisibility.PUBLIC,
				PostStatus.PUBLISHED,
				databaseSort
			)
			: draftPostRepository.findAllByVisibilityAndStatusAndCategory(
				PostVisibility.PUBLIC,
				PostStatus.PUBLISHED,
				category,
				databaseSort
			);
		Map<UUID, PublicPostCoverResponse> covers = publicCovers(posts);
		Map<PostCategory, Long> counts = categoryCounts();
		long restaurant = counts.getOrDefault(PostCategory.RESTAURANT, 0L);
		long destination = counts.getOrDefault(PostCategory.DESTINATION, 0L);
		return new PublicPostListResponse(
			new PublicPostCountsResponse(restaurant + destination, restaurant, destination),
			posts.stream()
				.map(post -> PublicPostSummaryResponse.from(post, covers.get(post.getId())))
				.toList()
		);
	}

	public PublicPostDetailResponse findPublicPost(UUID postId) {
		DraftPost post = requirePublicPost(postId);
		PublicPostPlaceResponse place = post.getPlaceId() == null
			? null
			: placeRepository.findById(post.getPlaceId()).map(PublicPostPlaceResponse::from).orElse(null);
		RestaurantDetailResponse restaurantDetails = post.getCategory() == PostCategory.RESTAURANT
			? restaurantDetailRepository.findById(post.getId()).map(RestaurantDetailResponse::from).orElse(null)
			: null;
		DestinationDetailResponse destinationDetails = post.getCategory() == PostCategory.DESTINATION
			? destinationDetailRepository.findById(post.getId()).map(DestinationDetailResponse::from).orElse(null)
			: null;
		return PublicPostDetailResponse.from(
			post,
			place,
			restaurantDetails,
			destinationDetails,
			publicDetailPhotos(post)
		);
	}

	public PublicPostCoverContent findPublicCover(UUID postId) {
		DraftPost post = requirePublicPost(postId, PublicPostService::coverNotFound);
		Photo photo = photoRepository.findByPostIdAndCoverTrueAndProcessingStatus(
			post.getId(),
			PhotoProcessingStatus.READY
		).orElseThrow(PublicPostService::coverNotFound);
		PhotoAsset asset = photoAssetRepository
			.findByPhotoIdAndVariantTypeAndAccessLevelAndMetadataScanPassedTrueAndWatermarkAppliedTrue(
				photo.getId(),
				PhotoAssetVariantType.MAP_CARD,
				PUBLIC_ACCESS
			)
			.filter(this::usesCurrentWatermark)
			.orElseThrow(PublicPostService::coverNotFound);
		try {
			return new PublicPostCoverContent(
				privatePhotoStorage.downloadResponsiveVariant(asset.getStorageKey()),
				asset.getMimeType()
			);
		} catch (StorageAccessException exception) {
			throw new PublicPostException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"PUBLIC_POST_COVER_UNAVAILABLE",
				"대표 사진을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
			);
		}
	}

	public PublicPostPhotoContent findPublicDetailPhoto(UUID postId, UUID photoId) {
		DraftPost post = requirePublicPost(postId, PublicPostService::photoNotFound);
		Photo photo = photoRepository.findByIdAndPostIdAndProcessingStatus(
			photoId,
			post.getId(),
			PhotoProcessingStatus.READY
		).orElseThrow(PublicPostService::photoNotFound);
		PhotoAsset asset = photoAssetRepository
			.findByPhotoIdAndVariantTypeAndAccessLevelAndMetadataScanPassedTrueAndWatermarkAppliedTrue(
				photo.getId(),
				PhotoAssetVariantType.PUBLIC_DETAIL,
				PUBLIC_ACCESS
			)
			.filter(this::usesCurrentWatermark)
			.orElseThrow(PublicPostService::photoNotFound);
		try {
			return new PublicPostPhotoContent(
				privatePhotoStorage.downloadResponsiveVariant(asset.getStorageKey()),
				asset.getMimeType()
			);
		} catch (StorageAccessException exception) {
			throw new PublicPostException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"PUBLIC_POST_PHOTO_UNAVAILABLE",
				"상세 사진을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
			);
		}
	}

	/**
	 * 公開カード用の代表写真だけを一括取得し、安全な派生画像を投稿ごとに索引化する。
	 */
	private Map<UUID, PublicPostCoverResponse> publicCovers(List<DraftPost> posts) {
		if (posts.isEmpty()) {
			return Map.of();
		}
		List<Photo> photos = photoRepository.findAllByPostIdInAndCoverTrueAndProcessingStatus(
			posts.stream().map(DraftPost::getId).toList(),
			PhotoProcessingStatus.READY
		);
		if (photos.isEmpty()) {
			return Map.of();
		}
		Map<UUID, PhotoAsset> assetsByPhotoId = new HashMap<>();
		for (PhotoAsset asset : photoAssetRepository
			.findAllByPhotoIdInAndVariantTypeAndAccessLevelAndMetadataScanPassedTrueAndWatermarkAppliedTrue(
				photos.stream().map(Photo::getId).toList(),
				PhotoAssetVariantType.MAP_CARD,
				PUBLIC_ACCESS
			)) {
			if (usesCurrentWatermark(asset)) {
				assetsByPhotoId.put(asset.getPhotoId(), asset);
			}
		}
		Map<UUID, Photo> photosByPostId = new HashMap<>();
		photos.forEach(photo -> photosByPostId.put(photo.getPostId(), photo));
		Map<UUID, PublicPostCoverResponse> covers = new HashMap<>();
		for (DraftPost post : posts) {
			Photo photo = photosByPostId.get(post.getId());
			PhotoAsset asset = photo == null ? null : assetsByPhotoId.get(photo.getId());
			if (photo != null && asset != null) {
				covers.put(post.getId(), PublicPostCoverResponse.from(post, photo, asset));
			}
		}
		return covers;
	}

	/**
	 * 公開詳細画面に表示できる検証済み写真だけを表示順で組み立てる。
	 */
	private List<PublicPostPhotoResponse> publicDetailPhotos(DraftPost post) {
		List<Photo> photos = photoRepository.findAllByPostIdAndProcessingStatusOrderByDisplayOrderAscCreatedAtAsc(
			post.getId(),
			PhotoProcessingStatus.READY
		);
		if (photos.isEmpty()) {
			return List.of();
		}
		Map<UUID, PhotoAsset> assetsByPhotoId = new HashMap<>();
		for (PhotoAsset asset : photoAssetRepository
			.findAllByPhotoIdInAndVariantTypeAndAccessLevelAndMetadataScanPassedTrueAndWatermarkAppliedTrue(
				photos.stream().map(Photo::getId).toList(),
				PhotoAssetVariantType.PUBLIC_DETAIL,
				PUBLIC_ACCESS
			)) {
			if (usesCurrentWatermark(asset)) {
				assetsByPhotoId.put(asset.getPhotoId(), asset);
			}
		}
		return photos.stream()
			.filter(photo -> assetsByPhotoId.containsKey(photo.getId()))
			.map(photo -> PublicPostPhotoResponse.from(post, photo, assetsByPhotoId.get(photo.getId())))
			.toList();
	}

	private boolean usesCurrentWatermark(PhotoAsset asset) {
		return asset.usesWatermarkPolicy(watermarkVersion, WATERMARK_POSITION);
	}

	private static Sort publishedAtSort(PublicPostSort sort) {
		Sort.Direction direction = sort == PublicPostSort.OLDEST
			? Sort.Direction.ASC
			: Sort.Direction.DESC;
		return Sort.by(direction, "publishedAt").and(Sort.by(direction, "id"));
	}

	private DraftPost requirePublicPost(UUID postId) {
		return requirePublicPost(postId, PublicPostService::postNotFound);
	}

	private DraftPost requirePublicPost(
		UUID postId,
		java.util.function.Supplier<PublicPostException> notFound
	) {
		return draftPostRepository.findByIdAndVisibilityAndStatus(
			postId,
			PostVisibility.PUBLIC,
			PostStatus.PUBLISHED
		).orElseThrow(notFound);
	}

	private static PublicPostException postNotFound() {
		return new PublicPostException(
			HttpStatus.NOT_FOUND,
			"PUBLIC_POST_NOT_FOUND",
			"공개 게시물을 찾을 수 없습니다."
		);
	}

	private static PublicPostException coverNotFound() {
		return new PublicPostException(
			HttpStatus.NOT_FOUND,
			"PUBLIC_POST_COVER_NOT_FOUND",
			"공개 대표 사진을 찾을 수 없습니다."
		);
	}

	private static PublicPostException photoNotFound() {
		return new PublicPostException(
			HttpStatus.NOT_FOUND,
			"PUBLIC_POST_PHOTO_NOT_FOUND",
			"공개 상세 사진을 찾을 수 없습니다."
		);
	}

	/**
	 * 公開範囲と公開状態を明示した集約結果をカテゴリごとに索引化する。
	 */
	private Map<PostCategory, Long> categoryCounts() {
		Map<PostCategory, Long> counts = new EnumMap<>(PostCategory.class);
		for (PostCategoryCount count : draftPostRepository.countByVisibilityAndStatusGroupedByCategory(
			PostVisibility.PUBLIC,
			PostStatus.PUBLISHED
		)) {
			counts.put(count.getCategory(), count.getTotal());
		}
		return counts;
	}
}
