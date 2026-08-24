package com.placesplates.domain.post.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.placesplates.domain.post.entity.DraftPost;
import com.placesplates.domain.post.entity.PostStatus;

public interface DraftPostRepository extends JpaRepository<DraftPost, UUID> {

	List<DraftPost> findAllByOwnerUserIdAndStatusOrderByUpdatedAtDesc(UUID ownerUserId, PostStatus status);

	Optional<DraftPost> findByIdAndOwnerUserIdAndStatus(UUID id, UUID ownerUserId, PostStatus status);
}
