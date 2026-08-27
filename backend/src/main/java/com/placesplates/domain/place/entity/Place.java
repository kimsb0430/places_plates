package com.placesplates.domain.place.entity;

import java.math.BigDecimal;
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
@Table(name = "places")
public class Place {

	@Id
	private UUID id;

	@Column(name = "created_by_user_id")
	private UUID createdByUserId;

	@Column(name = "google_place_id", length = 255, unique = true)
	private String googlePlaceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlaceSource source;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "place_type", length = 80)
	private String placeType;

	@Column(name = "formatted_address", length = 500)
	private String formattedAddress;

	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "google_maps_url", length = 1000)
	private String googleMapsUrl;

	@Column(name = "refreshed_at")
	private OffsetDateTime refreshedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Place() {
	}

	private Place(UUID createdByUserId, PlaceSource source, String name) {
		this.id = UUID.randomUUID();
		this.createdByUserId = createdByUserId;
		this.source = source;
		this.name = name.trim();
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public static Place google(
		UUID createdByUserId,
		String googlePlaceId,
		String name,
		String placeType,
		String formattedAddress,
		BigDecimal latitude,
		BigDecimal longitude,
		String googleMapsUrl
	) {
		Place place = new Place(createdByUserId, PlaceSource.GOOGLE, name);
		place.googlePlaceId = googlePlaceId.trim();
		place.refreshGoogleSnapshot(
			name,
			placeType,
			formattedAddress,
			latitude,
			longitude,
			googleMapsUrl
		);
		return place;
	}

	public static Place manual(
		UUID createdByUserId,
		String name,
		String formattedAddress,
		BigDecimal latitude,
		BigDecimal longitude,
		String googleMapsUrl
	) {
		Place place = new Place(createdByUserId, PlaceSource.MANUAL, name);
		place.formattedAddress = normalizeOptional(formattedAddress);
		place.latitude = latitude;
		place.longitude = longitude;
		place.googleMapsUrl = normalizeOptional(googleMapsUrl);
		return place;
	}

	/**
	 * Google Places由来の一時スナップショットと取得時刻を同時に更新する。
	 */
	public void refreshGoogleSnapshot(
		String name,
		String placeType,
		String formattedAddress,
		BigDecimal latitude,
		BigDecimal longitude,
		String googleMapsUrl
	) {
		this.name = name.trim();
		this.placeType = normalizeOptional(placeType);
		this.formattedAddress = normalizeOptional(formattedAddress);
		this.latitude = latitude;
		this.longitude = longitude;
		this.googleMapsUrl = normalizeOptional(googleMapsUrl);
		this.refreshedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	private static String normalizeOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public UUID getId() { return id; }
	public UUID getCreatedByUserId() { return createdByUserId; }
	public String getGooglePlaceId() { return googlePlaceId; }
	public PlaceSource getSource() { return source; }
	public String getName() { return name; }
	public String getPlaceType() { return placeType; }
	public String getFormattedAddress() { return formattedAddress; }
	public BigDecimal getLatitude() { return latitude; }
	public BigDecimal getLongitude() { return longitude; }
	public String getGoogleMapsUrl() { return googleMapsUrl; }
	public OffsetDateTime getRefreshedAt() { return refreshedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
