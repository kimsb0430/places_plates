package com.placesplates.domain.post.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;

import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostCategory;
import com.placesplates.domain.post.entity.PostStatus;
import com.placesplates.domain.post.entity.PostVisibility;

public interface DraftPostRepository extends JpaRepository<DraftPost, UUID> {

	List<DraftPost> findAllByOwnerUserIdAndStatusOrderByUpdatedAtDesc(UUID ownerUserId, PostStatus status);

	List<DraftPost> findAllByOwnerUserIdAndStatusOrderByPublishedAtDesc(UUID ownerUserId, PostStatus status);

	Optional<DraftPost> findByIdAndOwnerUserIdAndStatus(UUID id, UUID ownerUserId, PostStatus status);

	List<DraftPost> findAllByVisibilityAndStatus(
		PostVisibility visibility,
		PostStatus status,
		Sort sort
	);

	List<DraftPost> findAllByVisibilityAndStatusAndCategory(
		PostVisibility visibility,
		PostStatus status,
		PostCategory category,
		Sort sort
	);

	Optional<DraftPost> findByIdAndVisibilityAndStatus(
		UUID id,
		PostVisibility visibility,
		PostStatus status
	);

	List<DraftPost> findAllByOwnerUserIdAndPlaceIdAndVisibilityAndStatus(
		UUID ownerUserId,
		UUID placeId,
		PostVisibility visibility,
		PostStatus status,
		Sort sort
	);

	@Query("""
		select post.category as category, count(post) as total
		from DraftPost post
		where post.visibility = :visibility
		  and post.status = :status
		group by post.category
		""")
	List<PostCategoryCount> countByVisibilityAndStatusGroupedByCategory(
		@Param("visibility") PostVisibility visibility,
		@Param("status") PostStatus status
	);
}
