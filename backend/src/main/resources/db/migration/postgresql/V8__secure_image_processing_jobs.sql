ALTER TABLE image_processing_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE image_processing_jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY image_processing_jobs_owner_all ON image_processing_jobs
    FOR ALL
    USING (app_is_owner(owner_user_id))
    WITH CHECK (
        app_is_owner(owner_user_id)
        AND EXISTS (
            SELECT 1
            FROM posts
            WHERE posts.id = image_processing_jobs.post_id
              AND posts.owner_user_id = image_processing_jobs.owner_user_id
        )
        AND EXISTS (
            SELECT 1
            FROM upload_items
            JOIN upload_batches
              ON upload_batches.id = upload_items.upload_batch_id
            WHERE upload_items.id = image_processing_jobs.upload_item_id
              AND upload_batches.owner_user_id = image_processing_jobs.owner_user_id
              AND upload_batches.post_id = image_processing_jobs.post_id
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE image_processing_jobs TO placesplates_app;

DO $$
DECLARE
    exposed_role TEXT;
BEGIN
    FOREACH exposed_role IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (
            SELECT 1
            FROM pg_roles
            WHERE rolname = exposed_role
        ) THEN
            EXECUTE FORMAT(
                'REVOKE ALL PRIVILEGES ON TABLE image_processing_jobs FROM %I',
                exposed_role
            );
        END IF;
    END LOOP;
END
$$;
