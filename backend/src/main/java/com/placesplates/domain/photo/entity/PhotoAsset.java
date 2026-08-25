package com.placesplates.domain.photo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
	private String variantType;

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

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PhotoAsset() {
	}

	private PhotoAsset(
		UUID photoId,
		String storageKey,
		String mimeType,
		int width,
		int height,
		long byteSize
	) {
		this.id = UUID.randomUUID();
		this.photoId = photoId;
		this.variantType = "SANITIZED_MASTER";
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
		return new PhotoAsset(photoId, storageKey, mimeType, width, height, byteSize);
	}
}
