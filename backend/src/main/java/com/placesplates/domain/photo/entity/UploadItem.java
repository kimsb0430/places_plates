package com.placesplates.domain.photo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "upload_items")
public class UploadItem {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "upload_batch_id", nullable = false)
	private UploadBatch uploadBatch;

	@Column(name = "temporary_storage_key", length = 500)
	private String temporaryStorageKey;

	@Column(name = "client_file_label", nullable = false, length = 255)
	private String clientFileLabel;

	@Column(name = "mime_type", nullable = false, length = 100)
	private String mimeType;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "uploaded_bytes", nullable = false)
	private long uploadedBytes;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "failure_code", length = 50)
	private String failureCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private UploadItemStatus status;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected UploadItem() {
	}

	private UploadItem(
		String clientFileLabel,
		String mimeType,
		long byteSize,
		OffsetDateTime expiresAt
	) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.id = UUID.randomUUID();
		this.clientFileLabel = clientFileLabel;
		this.mimeType = mimeType;
		this.byteSize = byteSize;
		this.uploadedBytes = 0;
		this.attemptCount = 1;
		this.status = UploadItemStatus.PENDING;
		this.expiresAt = expiresAt;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static UploadItem create(
		String clientFileLabel,
		String mimeType,
		long byteSize,
		OffsetDateTime expiresAt
	) {
		return new UploadItem(clientFileLabel, mimeType, byteSize, expiresAt);
	}

	void assignTo(UploadBatch uploadBatch) {
		this.uploadBatch = uploadBatch;
	}

	public void start(String storageKey) {
		this.temporaryStorageKey = storageKey;
		this.status = UploadItemStatus.UPLOADING;
		this.failureCode = null;
		touch();
	}

	public void recordProgress(long uploadedBytes) {
		if (uploadedBytes < this.uploadedBytes || uploadedBytes > byteSize) {
			throw new IllegalArgumentException("Upload progress is outside the valid range");
		}
		this.uploadedBytes = uploadedBytes;
		this.status = UploadItemStatus.UPLOADING;
		touch();
	}

	public void markUploaded() {
		this.uploadedBytes = byteSize;
		this.status = UploadItemStatus.PROCESSING;
		this.failureCode = null;
		touch();
	}

	public void markFailed(String failureCode) {
		this.status = UploadItemStatus.FAILED;
		this.failureCode = failureCode;
		touch();
	}

	public void retry() {
		if (attemptCount >= 10) {
			throw new IllegalStateException("Upload retry limit reached");
		}
		attemptCount++;
		uploadedBytes = 0;
		status = UploadItemStatus.UPLOADING;
		failureCode = null;
		touch();
	}

	public boolean isUploaded() {
		return status == UploadItemStatus.PROCESSING || status == UploadItemStatus.COMPLETED;
	}

	private void touch() {
		updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public UUID getId() {
		return id;
	}

	public UploadBatch getUploadBatch() {
		return uploadBatch;
	}

	public String getTemporaryStorageKey() {
		return temporaryStorageKey;
	}

	public String getClientFileLabel() {
		return clientFileLabel;
	}

	public String getMimeType() {
		return mimeType;
	}

	public long getByteSize() {
		return byteSize;
	}

	public long getUploadedBytes() {
		return uploadedBytes;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public UploadItemStatus getStatus() {
		return status;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}
}
