CREATE TABLE image_processing_jobs (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    upload_item_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failure_code VARCHAR(80),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_image_processing_jobs_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_image_processing_jobs_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_image_processing_jobs_upload_item
        FOREIGN KEY (upload_item_id) REFERENCES upload_items (id) ON DELETE CASCADE,
    CONSTRAINT uk_image_processing_jobs_upload_item UNIQUE (upload_item_id),
    CONSTRAINT ck_image_processing_jobs_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_image_processing_jobs_attempts CHECK (
        attempt_count >= 0
        AND max_attempts BETWEEN 1 AND 10
        AND attempt_count <= max_attempts
    ),
    CONSTRAINT ck_image_processing_jobs_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX idx_image_processing_jobs_owner_status_ready
    ON image_processing_jobs (owner_user_id, status, next_attempt_at);

CREATE INDEX idx_image_processing_jobs_post_created
    ON image_processing_jobs (post_id, created_at DESC);
