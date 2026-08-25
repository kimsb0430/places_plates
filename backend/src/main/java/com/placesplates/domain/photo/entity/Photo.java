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
@Table(name = "photos")
public class Photo {

	@Id
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Column(name = "post_id")
	private UUID postId;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "is_cover", nullable = false)
	private boolean cover;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private PhotoProcessingStatus processingStatus;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Photo() {
	}

	private Photo(UUID ownerUserId, UUID postId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.id = UUID.randomUUID();
		this.ownerUserId = ownerUserId;
		this.postId = postId;
		this.displayOrder = 0;
		this.cover = false;
		this.processingStatus = PhotoProcessingStatus.PROCESSING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Photo processing(UUID ownerUserId, UUID postId) {
		return new Photo(ownerUserId, postId);
	}

	public void markReady() {
		this.processingStatus = PhotoProcessingStatus.READY;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public UUID getId() {
		return id;
	}

	public PhotoProcessingStatus getProcessingStatus() {
		return processingStatus;
	}
}
