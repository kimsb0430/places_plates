CREATE FUNCTION app_current_user_id()
RETURNS UUID
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT NULLIF(CURRENT_SETTING('app.current_user_id', TRUE), '')::UUID
$$;

CREATE FUNCTION app_request_mode()
RETURNS TEXT
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT COALESCE(NULLIF(CURRENT_SETTING('app.request_mode', TRUE), ''), 'NONE')
$$;

CREATE FUNCTION app_is_owner(candidate_user_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT app_request_mode() = 'OWNER'
       AND candidate_user_id IS NOT NULL
       AND candidate_user_id = app_current_user_id()
$$;

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY profiles_owner_all ON profiles
    FOR ALL
    USING (app_is_owner(user_id))
    WITH CHECK (app_is_owner(user_id));
CREATE POLICY profiles_public_select ON profiles
    FOR SELECT
    USING (app_request_mode() = 'PUBLIC' AND visibility = 'PUBLIC');

ALTER TABLE trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE trips FORCE ROW LEVEL SECURITY;
CREATE POLICY trips_owner_all ON trips
    FOR ALL
    USING (app_is_owner(owner_user_id))
    WITH CHECK (app_is_owner(owner_user_id));
CREATE POLICY trips_public_select ON trips
    FOR SELECT
    USING (app_request_mode() = 'PUBLIC' AND visibility = 'PUBLIC');

ALTER TABLE posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE posts FORCE ROW LEVEL SECURITY;
CREATE POLICY posts_owner_all ON posts
    FOR ALL
    USING (app_is_owner(owner_user_id))
    WITH CHECK (app_is_owner(owner_user_id));
CREATE POLICY posts_public_select ON posts
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND visibility = 'PUBLIC'
        AND status = 'PUBLISHED'
    );

ALTER TABLE places ENABLE ROW LEVEL SECURITY;
ALTER TABLE places FORCE ROW LEVEL SECURITY;
CREATE POLICY places_owner_select ON places
    FOR SELECT
    USING (
        app_request_mode() = 'OWNER'
        AND (
            app_is_owner(created_by_user_id)
            OR EXISTS (
                SELECT 1
                FROM posts
                WHERE posts.place_id = places.id
                  AND app_is_owner(posts.owner_user_id)
            )
        )
    );
CREATE POLICY places_owner_insert ON places
    FOR INSERT
    WITH CHECK (app_is_owner(created_by_user_id));
CREATE POLICY places_owner_update ON places
    FOR UPDATE
    USING (app_is_owner(created_by_user_id))
    WITH CHECK (app_is_owner(created_by_user_id));
CREATE POLICY places_owner_delete ON places
    FOR DELETE
    USING (app_is_owner(created_by_user_id));

ALTER TABLE restaurant_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE restaurant_details FORCE ROW LEVEL SECURITY;
CREATE POLICY restaurant_details_owner_all ON restaurant_details
    FOR ALL
    USING (
        EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = restaurant_details.post_id
              AND app_is_owner(posts.owner_user_id)
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = restaurant_details.post_id
              AND app_is_owner(posts.owner_user_id)
        )
    );
CREATE POLICY restaurant_details_public_select ON restaurant_details
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = restaurant_details.post_id
              AND posts.visibility = 'PUBLIC'
              AND posts.status = 'PUBLISHED'
        )
    );

ALTER TABLE destination_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE destination_details FORCE ROW LEVEL SECURITY;
CREATE POLICY destination_details_owner_all ON destination_details
    FOR ALL
    USING (
        EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = destination_details.post_id
              AND app_is_owner(posts.owner_user_id)
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = destination_details.post_id
              AND app_is_owner(posts.owner_user_id)
        )
    );
CREATE POLICY destination_details_public_select ON destination_details
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = destination_details.post_id
              AND posts.visibility = 'PUBLIC'
              AND posts.status = 'PUBLISHED'
        )
    );

ALTER TABLE tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags FORCE ROW LEVEL SECURITY;
CREATE POLICY tags_owner_select ON tags
    FOR SELECT
    USING (
        app_request_mode() = 'OWNER'
        AND (tag_type = 'SYSTEM' OR app_is_owner(owner_user_id))
    );
CREATE POLICY tags_owner_write ON tags
    FOR ALL
    USING (tag_type = 'USER' AND app_is_owner(owner_user_id))
    WITH CHECK (tag_type = 'USER' AND app_is_owner(owner_user_id));
CREATE POLICY tags_public_select ON tags
    FOR SELECT
    USING (app_request_mode() = 'PUBLIC' AND tag_type = 'SYSTEM');

ALTER TABLE post_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE post_tags FORCE ROW LEVEL SECURITY;
CREATE POLICY post_tags_owner_all ON post_tags
    FOR ALL
    USING (
        EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = post_tags.post_id
              AND app_is_owner(posts.owner_user_id)
        )
        AND EXISTS (
            SELECT 1
            FROM tags
            WHERE tags.id = post_tags.tag_id
              AND (tags.tag_type = 'SYSTEM' OR app_is_owner(tags.owner_user_id))
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = post_tags.post_id
              AND app_is_owner(posts.owner_user_id)
        )
        AND EXISTS (
            SELECT 1
            FROM tags
            WHERE tags.id = post_tags.tag_id
              AND (tags.tag_type = 'SYSTEM' OR app_is_owner(tags.owner_user_id))
        )
    );
CREATE POLICY post_tags_public_select ON post_tags
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = post_tags.post_id
              AND posts.visibility = 'PUBLIC'
              AND posts.status = 'PUBLISHED'
        )
    );

ALTER TABLE photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE photos FORCE ROW LEVEL SECURITY;
CREATE POLICY photos_owner_all ON photos
    FOR ALL
    USING (app_is_owner(owner_user_id))
    WITH CHECK (app_is_owner(owner_user_id));
CREATE POLICY photos_public_select ON photos
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND processing_status = 'READY'
        AND EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = photos.post_id
              AND posts.visibility = 'PUBLIC'
              AND posts.status = 'PUBLISHED'
        )
    );

ALTER TABLE photo_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE photo_assets FORCE ROW LEVEL SECURITY;
CREATE POLICY photo_assets_owner_all ON photo_assets
    FOR ALL
    USING (
        EXISTS (
            SELECT 1
            FROM photos
            WHERE photos.id = photo_assets.photo_id
              AND app_is_owner(photos.owner_user_id)
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM photos
            WHERE photos.id = photo_assets.photo_id
              AND app_is_owner(photos.owner_user_id)
        )
    );
CREATE POLICY photo_assets_public_select ON photo_assets
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND access_level = 'PUBLIC'
        AND metadata_scan_passed = TRUE
        AND watermark_applied = TRUE
        AND EXISTS (
            SELECT 1
            FROM photos
            JOIN posts ON posts.id = photos.post_id
            WHERE photos.id = photo_assets.photo_id
              AND photos.processing_status = 'READY'
              AND posts.visibility = 'PUBLIC'
              AND posts.status = 'PUBLISHED'
        )
    );

ALTER TABLE upload_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE upload_batches FORCE ROW LEVEL SECURITY;
CREATE POLICY upload_batches_owner_all ON upload_batches
    FOR ALL
    USING (app_is_owner(owner_user_id))
    WITH CHECK (app_is_owner(owner_user_id));

ALTER TABLE upload_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE upload_items FORCE ROW LEVEL SECURITY;
CREATE POLICY upload_items_owner_all ON upload_items
    FOR ALL
    USING (
        EXISTS (
            SELECT 1
            FROM upload_batches
            WHERE upload_batches.id = upload_items.upload_batch_id
              AND app_is_owner(upload_batches.owner_user_id)
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM upload_batches
            WHERE upload_batches.id = upload_items.upload_batch_id
              AND app_is_owner(upload_batches.owner_user_id)
        )
    );
