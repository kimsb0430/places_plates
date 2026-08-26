package com.placesplates.domain.photo.entity;

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
@Table(name = "photo_assets")
public class PhotoAsset {

	@Id
	private UUID id;

	@Column(name = "photo_id", nullable = false)
	private UUID photoId;

	@Column(name = "variant_type", nullable = false, length = 30)
	@Enumerated(EnumType.STRING)
	private PhotoAssetVariantType variantType;

	@Column(name = "access_level", nullable = false, length = 20)
	private String accessLevel;

	@Column(name = "storage_key", nullable = false, unique = true, length = 500)
	private String storageKey;

	@Column(name = "mime_type", nullable = false, length = 100)
	private String mimeType;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "metadata_scan_passed", nullable = false)
	private boolean metadataScanPassed;

	@Column(name = "watermark_applied", nullable = false)
	private boolean watermarkApplied;

	@Column(name = "watermark_version", length = 40)
	private String watermarkVersion;

	@Column(name = "watermark_position", length = 30)
	private String watermarkPosition;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PhotoAsset() {
	}

	private PhotoAsset(
		UUID photoId,
		PhotoAssetVariantType variantType,
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize
	) {
		this.id = UUID.randomUUID();
		this.photoId = photoId;
		this.variantType = variantType;
		this.accessLevel = "PRIVATE";
		this.storageKey = storageKey;
		this.mimeType = mimeType;
		this.width = width;
		this.height = height;
		this.byteSize = byteSize;
		this.metadataScanPassed = true;
		this.watermarkApplied = false;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public static PhotoAsset sanitizedMaster(
		UUID photoId,
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize
	) {
		return new PhotoAsset(
			photoId,
			PhotoAssetVariantType.SANITIZED_MASTER,
			storageKey,
			mimeType,
			width,
			height,
			byteSize
		);
	}

	public static PhotoAsset privateResponsiveVariant(
		UUID photoId,
		PhotoAssetVariantType variantType,
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize
	) {
		if (!variantType.isResponsiveVariant()) {
			throw new IllegalArgumentException("Responsive asset type is required");
		}
		return new PhotoAsset(photoId, variantType, storageKey, mimeType, width, height, byteSize);
	}

	public static PhotoAsset publicWatermarkedVariant(
		UUID photoId,
		PhotoAssetVariantType variantType,
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize,
		String watermarkVersion,
		String watermarkPosition
	) {
		PhotoAsset asset = privateResponsiveVariant(
			photoId,
			variantType,
			storageKey,
			mimeType,
			width,
			height,
			byteSize
		);
		asset.publishWatermarked(
			storageKey,
			mimeType,
			width,
			height,
			byteSize,
			watermarkVersion,
			watermarkPosition
		);
		return asset;
	}

	public void publishWatermarked(
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize,
		String watermarkVersion,
		String watermarkPosition
	) {
		if (!variantType.isResponsiveVariant()) {
			throw new IllegalStateException("Only responsive assets can be published with a watermark");
		}
		if (watermarkVersion == null || watermarkVersion.isBlank()) {
			throw new IllegalArgumentException("Watermark version is required");
		}
		if (watermarkPosition == null || watermarkPosition.isBlank()) {
			throw new IllegalArgumentException("Watermark position is required");
		}
		this.accessLevel = "PUBLIC";
		this.storageKey = storageKey;
		this.mimeType = mimeType;
		this.width = width;
		this.height = height;
		this.byteSize = byteSize;
		this.metadataScanPassed = true;
		this.watermarkApplied = true;
		this.watermarkVersion = watermarkVersion;
		this.watermarkPosition = watermarkPosition;
	}

	public void stagePrivateVariant(
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize
	) {
		if (!variantType.isResponsiveVariant()) {
			throw new IllegalStateException("Only responsive assets can be staged");
		}
		this.accessLevel = "PRIVATE";
		this.storageKey = storageKey;
		this.mimeType = mimeType;
		this.width = width;
		this.height = height;
		this.byteSize = byteSize;
		this.metadataScanPassed = true;
		this.watermarkApplied = false;
		this.watermarkVersion = null;
		this.watermarkPosition = null;
	}

	public boolean usesWatermarkPolicy(String version, String position) {
		return "PUBLIC".equals(accessLevel)
			&& metadataScanPassed
			&& watermarkApplied
			&& version.equals(watermarkVersion)
			&& position.equals(watermarkPosition);
	}

	public PhotoAssetVariantType getVariantType() {
		return variantType;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getMimeType() {
		return mimeType;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public long getByteSize() {
		return byteSize;
	}

	public String getAccessLevel() {
		return accessLevel;
	}

	public boolean isMetadataScanPassed() {
		return metadataScanPassed;
	}

	public boolean isWatermarkApplied() {
		return watermarkApplied;
	}

	public String getWatermarkVersion() {
		return watermarkVersion;
	}

	public String getWatermarkPosition() {
		return watermarkPosition;
	}
}
