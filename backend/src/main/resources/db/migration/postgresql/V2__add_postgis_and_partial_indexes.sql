CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE places
    ADD COLUMN location geography(Point, 4326)
    GENERATED ALWAYS AS (
        CASE
            WHEN longitude IS NULL OR latitude IS NULL THEN NULL
            ELSE ST_SetSRID(
                ST_MakePoint(
                    CAST(longitude AS DOUBLE PRECISION),
                    CAST(latitude AS DOUBLE PRECISION)
                ),
                4326
            )::geography
        END
    ) STORED;

CREATE INDEX idx_places_location_gist
    ON places USING GIST (location);

CREATE INDEX idx_posts_public_owner_published
    ON posts (owner_user_id, published_at DESC)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC';

CREATE INDEX idx_posts_public_owner_category_published
    ON posts (owner_user_id, category, published_at DESC)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC';

CREATE INDEX idx_posts_public_owner_place_category
    ON posts (owner_user_id, place_id, category)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND place_id IS NOT NULL;

CREATE UNIQUE INDEX uk_photos_one_cover_per_post
    ON photos (post_id)
    WHERE is_cover = TRUE;

CREATE UNIQUE INDEX uk_tags_system_slug
    ON tags (slug)
    WHERE owner_user_id IS NULL;

CREATE INDEX idx_upload_batches_active_expires
    ON upload_batches (expires_at)
    WHERE status IN ('PENDING', 'UPLOADING', 'PROCESSING');

CREATE INDEX idx_upload_items_active_expires
    ON upload_items (expires_at)
    WHERE processing_status IN ('PENDING', 'UPLOADING', 'PROCESSING', 'FAILED');

CREATE FUNCTION enforce_post_detail_category()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    expected_category VARCHAR(20);
    actual_category VARCHAR(20);
BEGIN
    expected_category := CASE TG_TABLE_NAME
        WHEN 'restaurant_details' THEN 'RESTAURANT'
        WHEN 'destination_details' THEN 'DESTINATION'
    END;

    SELECT category INTO actual_category
    FROM posts
    WHERE id = NEW.post_id;

    IF actual_category IS DISTINCT FROM expected_category THEN
        RAISE EXCEPTION 'Post category % cannot use %', actual_category, TG_TABLE_NAME;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_restaurant_details_category
    BEFORE INSERT OR UPDATE ON restaurant_details
    FOR EACH ROW EXECUTE FUNCTION enforce_post_detail_category();

CREATE TRIGGER trg_destination_details_category
    BEFORE INSERT OR UPDATE ON destination_details
    FOR EACH ROW EXECUTE FUNCTION enforce_post_detail_category();

CREATE FUNCTION prevent_incompatible_post_category_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.category = 'RESTAURANT'
        AND EXISTS (SELECT 1 FROM destination_details WHERE post_id = NEW.id) THEN
        RAISE EXCEPTION 'Destination detail must be removed before changing post category';
    END IF;

    IF NEW.category = 'DESTINATION'
        AND EXISTS (SELECT 1 FROM restaurant_details WHERE post_id = NEW.id) THEN
        RAISE EXCEPTION 'Restaurant detail must be removed before changing post category';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_posts_category_change
    BEFORE UPDATE OF category ON posts
    FOR EACH ROW
    WHEN (OLD.category IS DISTINCT FROM NEW.category)
    EXECUTE FUNCTION prevent_incompatible_post_category_change();
