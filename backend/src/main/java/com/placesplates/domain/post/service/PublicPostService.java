package com.placesplates.domain.post.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.placesplates.domain.post.dto.PublicPostCountsResponse;
import com.placesplates.domain.post.dto.PublicPostListResponse;
import com.placesplates.domain.post.dto.PublicPostSummaryResponse;
import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.PostVisibility;
import com.placesplates.domain.post.repository.DraftPostRepository;
import com.placesplates.domain.post.repository.PostCategoryCount;

@Service
@Transactional(readOnly = true)
public class PublicPostService {

	private final DraftPostRepository draftPostRepository;

	public PublicPostService(DraftPostRepository draftPostRepository) {
		this.draftPostRepository = draftPostRepository;
	}

	public PublicPostListResponse findPublicPosts(PostCategory category) {
		List<DraftPost> posts = category == null
			? draftPostRepository.findAllByVisibilityAndStatusOrderByPublishedAtDesc(
				PostVisibility.PUBLIC,
				PostStatus.PUBLISHED
			)
			: draftPostRepository.findAllByVisibilityAndStatusAndCategoryOrderByPublishedAtDesc(
				PostVisibility.PUBLIC,
				PostStatus.PUBLISHED,
				category
			);
		Map<PostCategory, Long> counts = categoryCounts();
		long restaurant = counts.getOrDefault(PostCategory.RESTAURANT, 0L);
		long destination = counts.getOrDefault(PostCategory.DESTINATION, 0L);
		return new PublicPostListResponse(
			new PublicPostCountsResponse(restaurant + destination, restaurant, destination),
			posts.stream().map(PublicPostSummaryResponse::from).toList()
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
