package com.placesplates.domain.photo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "upload_batches")
public class UploadBatch {

	@Id
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Column(name = "post_id")
	private UUID postId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UploadBatchStatus status;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@OneToMany(mappedBy = "uploadBatch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<UploadItem> items = new ArrayList<>();

	protected UploadBatch() {
	}

	private UploadBatch(UUID ownerUserId, OffsetDateTime expiresAt) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.id = UUID.randomUUID();
		this.ownerUserId = ownerUserId;
		this.status = UploadBatchStatus.UPLOADING;
		this.expiresAt = expiresAt;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static UploadBatch create(UUID ownerUserId, OffsetDateTime expiresAt) {
		return new UploadBatch(ownerUserId, expiresAt);
	}

	public void addItem(UploadItem item) {
		items.add(item);
		item.assignTo(this);
		touch();
	}

	public void refreshStatus() {
		if (items.stream().allMatch(item -> item.getStatus() == UploadItemStatus.EXPIRED)) {
			status = UploadBatchStatus.EXPIRED;
		} else if (items.stream().allMatch(UploadItem::isUploaded)) {
			status = UploadBatchStatus.PROCESSING;
		} else if (items.stream().allMatch(item -> item.getStatus() == UploadItemStatus.FAILED)) {
			status = UploadBatchStatus.FAILED;
		} else {
			status = UploadBatchStatus.UPLOADING;
		}
		touch();
	}

	private void touch() {
		updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public UploadBatchStatus getStatus() {
		return status;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public List<UploadItem> getItems() {
		return Collections.unmodifiableList(items);
	}
}
