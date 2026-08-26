package com.placesplates.domain.post.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant_details")
public class RestaurantDetail {

	@Id
	@Column(name = "post_id")
	private UUID postId;

	@Column(precision = 3, scale = 1)
	private BigDecimal rating;

	@Column(name = "recommended_menu", length = 300)
	private String recommendedMenu;

	@Enumerated(EnumType.STRING)
	@Column(name = "price_range", length = 20)
	private RestaurantPriceRange priceRange;

	@Column(name = "waiting_minutes")
	private Integer waitingMinutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "revisit_intention", length = 20)
	private RevisitIntention revisitIntention;

	protected RestaurantDetail() {
	}

	private RestaurantDetail(UUID postId) {
		this.postId = postId;
	}

	public static RestaurantDetail create(UUID postId) {
		return new RestaurantDetail(postId);
	}

	/**
	 * レストラン固有の任意項目を一括で更新する。
	 */
	public void update(
		BigDecimal rating,
		String recommendedMenu,
		RestaurantPriceRange priceRange,
		Integer waitingMinutes,
		RevisitIntention revisitIntention
	) {
		this.rating = rating;
		this.recommendedMenu = normalizeOptionalText(recommendedMenu);
		this.priceRange = priceRange;
		this.waitingMinutes = waitingMinutes;
		this.revisitIntention = revisitIntention;
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

	public BigDecimal getRating() {
		return rating;
	}

	public String getRecommendedMenu() {
		return recommendedMenu;
	}

	public RestaurantPriceRange getPriceRange() {
		return priceRange;
	}

	public Integer getWaitingMinutes() {
		return waitingMinutes;
	}

	public RevisitIntention getRevisitIntention() {
		return revisitIntention;
	}
}
