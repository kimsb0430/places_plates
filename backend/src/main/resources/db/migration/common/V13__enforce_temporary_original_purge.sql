ALTER TABLE upload_items
    ADD CONSTRAINT ck_upload_items_expired_original_deleted CHECK (
        processing_status <> 'EXPIRED'
        OR (
            original_deleted_at IS NOT NULL
            AND temporary_storage_key IS NULL
            AND result_photo_id IS NULL
        )
    );

CREATE INDEX idx_upload_items_original_cleanup
    ON upload_items (original_deleted_at, expires_at, processing_status);
