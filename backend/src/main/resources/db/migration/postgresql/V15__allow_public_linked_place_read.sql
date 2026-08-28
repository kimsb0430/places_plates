CREATE POLICY places_public_select ON places
    FOR SELECT
    USING (
        app_request_mode() = 'PUBLIC'
        AND EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.place_id = places.id
              AND posts.visibility = 'PUBLIC'
              AND posts.status = 'PUBLISHED'
        )
    );
