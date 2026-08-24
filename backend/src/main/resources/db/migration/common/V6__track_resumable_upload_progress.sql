ALTER TABLE upload_items ADD COLUMN client_file_label VARCHAR(255) NOT NULL DEFAULT 'photo';
ALTER TABLE upload_items ADD COLUMN mime_type VARCHAR(100) NOT NULL DEFAULT 'application/octet-stream';
ALTER TABLE upload_items ADD COLUMN byte_size BIGINT NOT NULL DEFAULT 1;
ALTER TABLE upload_items ADD COLUMN uploaded_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE upload_items ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 1;
ALTER TABLE upload_items ADD COLUMN failure_code VARCHAR(50);

ALTER TABLE upload_items
    ADD CONSTRAINT ck_upload_items_byte_size CHECK (byte_size > 0);

ALTER TABLE upload_items
    ADD CONSTRAINT ck_upload_items_uploaded_bytes CHECK (
        uploaded_bytes >= 0
        AND uploaded_bytes <= byte_size
    );

ALTER TABLE upload_items
    ADD CONSTRAINT ck_upload_items_attempt_count CHECK (attempt_count BETWEEN 1 AND 10);

CREATE INDEX idx_upload_items_expiry_status
    ON upload_items (expires_at, processing_status);
