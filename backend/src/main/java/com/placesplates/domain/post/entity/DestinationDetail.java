package com.placesplates.domain.post.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "destination_details")
public class DestinationDetail {

	@Id
	@Column(name = "post_id")
	private UUID postId;

	@Column(name = "recommended_time", length = 100)
	private String recommendedTime;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(columnDefinition = "TEXT")
	private String highlights;

	@Column(name = "travel_tips", columnDefinition = "TEXT")
	private String travelTips;

	protected DestinationDetail() {
	}

	private DestinationDetail(UUID postId) {
		this.postId = postId;
	}

	public static DestinationDetail create(UUID postId) {
		return new DestinationDetail(postId);
	}

	/**
	 * 旅行先固有の任意項目を一括で更新する。
	 */
	public void update(
		String recommendedTime,
		Integer durationMinutes,
		String highlights,
		String travelTips
	) {
		this.recommendedTime = normalizeOptionalText(recommendedTime);
		this.durationMinutes = durationMinutes;
		this.highlights = normalizeOptionalText(highlights);
		this.travelTips = normalizeOptionalText(travelTips);
	}

	private static String normalizeOptionalText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public UUID getPostId() {
		return postId;
	}

	public String getRecommendedTime() {
		return recommendedTime;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public String getHighlights() {
		return highlights;
	}

	public String getTravelTips() {
		return travelTips;
	}
}
