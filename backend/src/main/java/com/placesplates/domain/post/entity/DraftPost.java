package com.placesplates.domain.post.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "posts")
public class DraftPost {

	@Id
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PostCategory category;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PostVisibility visibility;

	@Column(name = "coordinate_visibility", nullable = false, length = 20)
	private String coordinateVisibility;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PostStatus status;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected DraftPost() {
	}

	private DraftPost(UUID ownerUserId, PostCategory category) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.id = UUID.randomUUID();
		this.ownerUserId = ownerUserId;
		this.category = category;
		this.title = category == PostCategory.RESTAURANT ? "새 맛집 기록" : "새 여행지 기록";
		this.visibility = PostVisibility.PRIVATE;
		this.coordinateVisibility = "HIDDEN";
		this.status = PostStatus.DRAFT;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static DraftPost create(UUID ownerUserId, PostCategory category) {
		return new DraftPost(ownerUserId, category);
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public PostCategory getCategory() {
		return category;
	}

	public String getTitle() {
		return title;
	}

	public PostVisibility getVisibility() {
		return visibility;
	}

	public PostStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
