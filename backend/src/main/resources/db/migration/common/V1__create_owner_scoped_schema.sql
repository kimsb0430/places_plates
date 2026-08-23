CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
);

CREATE TABLE profiles (
    user_id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    bio VARCHAR(500),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT uk_profiles_slug UNIQUE (slug),
    CONSTRAINT ck_profiles_slug_lowercase CHECK (slug = LOWER(slug)),
    CONSTRAINT ck_profiles_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'))
);

CREATE TABLE trips (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    started_on DATE,
    ended_on DATE,
    public_period_label VARCHAR(80),
    summary VARCHAR(500),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trips_owner FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT uk_trips_owner_slug UNIQUE (owner_user_id, slug),
    CONSTRAINT ck_trips_slug_lowercase CHECK (slug = LOWER(slug)),
    CONSTRAINT ck_trips_period CHECK (ended_on IS NULL OR started_on IS NULL OR ended_on >= started_on),
    CONSTRAINT ck_trips_visibility CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC'))
);

CREATE TABLE places (
    id UUID PRIMARY KEY,
    created_by_user_id UUID,
    google_place_id VARCHAR(255),
    source VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    place_type VARCHAR(80),
    formatted_address VARCHAR(500),
    latitude DECIMAL(9, 6),
    longitude DECIMAL(9, 6),
    google_maps_url VARCHAR(1000),
    refreshed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_places_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users (id),
    CONSTRAINT uk_places_google_place_id UNIQUE (google_place_id),
    CONSTRAINT ck_places_source CHECK (source IN ('GOOGLE', 'MANUAL')),
    CONSTRAINT ck_places_google_source CHECK (source <> 'GOOGLE' OR google_place_id IS NOT NULL),
    CONSTRAINT ck_places_coordinate_pair CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    CONSTRAINT ck_places_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_places_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE TABLE posts (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    trip_id UUID,
    place_id UUID,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500),
    content TEXT,
    visited_on DATE,
    public_visit_year SMALLINT,
    public_visit_month SMALLINT,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    coordinate_visibility VARCHAR(20) NOT NULL DEFAULT 'HIDDEN',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    trip_order INTEGER,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_posts_owner FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_posts_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE SET NULL,
    CONSTRAINT fk_posts_place FOREIGN KEY (place_id) REFERENCES places (id),
    CONSTRAINT uk_posts_trip_order UNIQUE (trip_id, trip_order),
    CONSTRAINT ck_posts_category CHECK (category IN ('RESTAURANT', 'DESTINATION')),
    CONSTRAINT ck_posts_visibility CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC')),
    CONSTRAINT ck_posts_coordinate_visibility CHECK (coordinate_visibility IN ('EXACT', 'APPROXIMATE', 'HIDDEN')),
    CONSTRAINT ck_posts_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_posts_public_month_pair CHECK (
        (public_visit_year IS NULL AND public_visit_month IS NULL)
        OR (
            public_visit_year BETWEEN 1000 AND 9999
            AND public_visit_month BETWEEN 1 AND 12
        )
    ),
    CONSTRAINT ck_posts_trip_order CHECK (trip_order IS NULL OR trip_order >= 0),
    CONSTRAINT ck_posts_publish_fields CHECK (
        status <> 'PUBLISHED'
        OR (
            place_id IS NOT NULL
            AND public_visit_year IS NOT NULL
            AND public_visit_month IS NOT NULL
            AND published_at IS NOT NULL
        )
    )
);

CREATE TABLE restaurant_details (
    post_id UUID PRIMARY KEY,
    rating DECIMAL(3, 1),
    recommended_menu VARCHAR(300),
    price_range VARCHAR(20),
    waiting_minutes INTEGER,
    revisit_intention VARCHAR(20),
    CONSTRAINT fk_restaurant_details_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT ck_restaurant_details_rating CHECK (rating IS NULL OR rating BETWEEN 0 AND 5),
    CONSTRAINT ck_restaurant_details_price_range CHECK (
        price_range IS NULL OR price_range IN ('BUDGET', 'MODERATE', 'EXPENSIVE', 'LUXURY')
    ),
    CONSTRAINT ck_restaurant_details_waiting CHECK (waiting_minutes IS NULL OR waiting_minutes >= 0),
    CONSTRAINT ck_restaurant_details_revisit CHECK (
        revisit_intention IS NULL OR revisit_intention IN ('YES', 'MAYBE', 'NO')
    )
);

CREATE TABLE destination_details (
    post_id UUID PRIMARY KEY,
    recommended_time VARCHAR(100),
    duration_minutes INTEGER,
    highlights TEXT,
    travel_tips TEXT,
    season VARCHAR(80),
    CONSTRAINT fk_destination_details_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT ck_destination_details_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0)
);

CREATE TABLE tags (
    id UUID PRIMARY KEY,
    owner_user_id UUID,
    tag_type VARCHAR(20) NOT NULL,
    name VARCHAR(80) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    CONSTRAINT fk_tags_owner FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT uk_tags_owner_slug UNIQUE (owner_user_id, slug),
    CONSTRAINT ck_tags_type CHECK (tag_type IN ('SYSTEM', 'USER')),
    CONSTRAINT ck_tags_owner CHECK (
        (tag_type = 'SYSTEM' AND owner_user_id IS NULL)
        OR (tag_type = 'USER' AND owner_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_tags_slug_lowercase CHECK (slug = LOWER(slug))
);

CREATE TABLE post_tags (
    post_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE TABLE upload_batches (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    post_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upload_batches_owner FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_upload_batches_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE SET NULL,
    CONSTRAINT ck_upload_batches_status CHECK (
        status IN ('PENDING', 'UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')
    )
);

CREATE TABLE photos (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    post_id UUID,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    alt_text VARCHAR(500),
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photos_owner FOREIGN KEY (owner_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_photos_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE SET NULL,
    CONSTRAINT ck_photos_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_photos_cover_post CHECK (is_cover = FALSE OR post_id IS NOT NULL),
    CONSTRAINT ck_photos_processing_status CHECK (
        processing_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')
    )
);

CREATE TABLE upload_items (
    id UUID PRIMARY KEY,
    upload_batch_id UUID NOT NULL,
    result_photo_id UUID,
    temporary_storage_key VARCHAR(500),
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    original_deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upload_items_batch FOREIGN KEY (upload_batch_id) REFERENCES upload_batches (id) ON DELETE CASCADE,
    CONSTRAINT fk_upload_items_photo FOREIGN KEY (result_photo_id) REFERENCES photos (id) ON DELETE SET NULL,
    CONSTRAINT uk_upload_items_result_photo UNIQUE (result_photo_id),
    CONSTRAINT ck_upload_items_processing_status CHECK (
        processing_status IN ('PENDING', 'UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')
    ),
    CONSTRAINT ck_upload_items_deleted_key CHECK (
        original_deleted_at IS NULL OR temporary_storage_key IS NULL
    ),
    CONSTRAINT ck_upload_items_completed CHECK (
        processing_status <> 'COMPLETED'
        OR (
            original_deleted_at IS NOT NULL
            AND temporary_storage_key IS NULL
        )
    )
);

CREATE TABLE photo_assets (
    id UUID PRIMARY KEY,
    photo_id UUID NOT NULL,
    variant_type VARCHAR(30) NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    byte_size BIGINT NOT NULL,
    metadata_scan_passed BOOLEAN NOT NULL DEFAULT FALSE,
    watermark_applied BOOLEAN NOT NULL DEFAULT FALSE,
    watermark_version VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photo_assets_photo FOREIGN KEY (photo_id) REFERENCES photos (id) ON DELETE CASCADE,
    CONSTRAINT uk_photo_assets_photo_variant UNIQUE (photo_id, variant_type),
    CONSTRAINT uk_photo_assets_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_photo_assets_variant CHECK (
        variant_type IN ('SANITIZED_MASTER', 'PUBLIC_DETAIL', 'THUMBNAIL', 'MAP_CARD')
    ),
    CONSTRAINT ck_photo_assets_access CHECK (access_level IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_photo_assets_dimensions CHECK (width > 0 AND height > 0 AND byte_size > 0),
    CONSTRAINT ck_photo_assets_master_private CHECK (
        variant_type <> 'SANITIZED_MASTER' OR access_level = 'PRIVATE'
    ),
    CONSTRAINT ck_photo_assets_public_safe CHECK (
        access_level <> 'PUBLIC' OR (metadata_scan_passed = TRUE AND watermark_applied = TRUE)
    )
);

CREATE INDEX idx_trips_owner_started ON trips (owner_user_id, started_on DESC);
CREATE INDEX idx_places_creator ON places (created_by_user_id);
CREATE INDEX idx_posts_owner_status_updated ON posts (owner_user_id, status, updated_at DESC);
CREATE INDEX idx_posts_place_category ON posts (place_id, category);
CREATE INDEX idx_post_tags_tag_post ON post_tags (tag_id, post_id);
CREATE INDEX idx_photos_post_display_order ON photos (post_id, display_order);
CREATE INDEX idx_photos_owner_processing_status ON photos (owner_user_id, processing_status);
CREATE INDEX idx_upload_batches_owner_created ON upload_batches (owner_user_id, created_at DESC);
CREATE INDEX idx_upload_batches_post_created ON upload_batches (post_id, created_at DESC);
CREATE INDEX idx_upload_items_batch_status ON upload_items (upload_batch_id, processing_status);
