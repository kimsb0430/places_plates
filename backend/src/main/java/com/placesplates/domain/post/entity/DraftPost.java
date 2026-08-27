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

	@Column(name = "place_id")
	private UUID placeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PostCategory category;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 500)
	private String summary;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "public_visit_year")
	private Short publicVisitYear;

	@Column(name = "public_visit_month")
	private Short publicVisitMonth;

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

	/**
	 * 編集画面の共通項目を更新し、最後の保存時刻をUTCで記録する。
	 */
	public void updateEditorFields(
		String title,
		String summary,
		String content,
		Integer publicVisitYear,
		Integer publicVisitMonth
	) {
		this.title = title.trim();
		this.summary = normalizeOptionalText(summary);
		this.content = normalizeOptionalText(content);
		this.publicVisitYear = publicVisitYear == null ? null : publicVisitYear.shortValue();
		this.publicVisitMonth = publicVisitMonth == null ? null : publicVisitMonth.shortValue();
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	/**
	 * 下書きへ場所を接続し、編集時刻を更新する。
	 */
	public void connectPlace(UUID placeId) {
		this.placeId = placeId;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	/**
	 * 下書きから場所接続を解除し、編集時刻を更新する。
	 */
	public void disconnectPlace() {
		this.placeId = null;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	private static String normalizeOptionalText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public UUID getPlaceId() {
		return placeId;
	}

	public PostCategory getCategory() {
		return category;
	}

	public String getTitle() {
		return title;
	}

	public String getSummary() {
		return summary;
	}

	public String getContent() {
		return content;
	}

	public Short getPublicVisitYear() {
		return publicVisitYear;
	}

	public Short getPublicVisitMonth() {
		return publicVisitMonth;
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
