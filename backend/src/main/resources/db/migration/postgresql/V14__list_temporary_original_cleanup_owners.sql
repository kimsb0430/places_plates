CREATE FUNCTION list_temporary_original_cleanup_owners(candidate_limit INTEGER)
RETURNS TABLE(owner_user_id UUID)
LANGUAGE SQL
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT upload_batches.owner_user_id
    FROM upload_batches
    JOIN upload_items
      ON upload_items.upload_batch_id = upload_batches.id
    WHERE upload_items.original_deleted_at IS NULL
      AND upload_items.temporary_storage_key IS NOT NULL
      AND (
          upload_items.result_photo_id IS NOT NULL
          OR upload_items.expires_at <= CURRENT_TIMESTAMP
      )
    GROUP BY upload_batches.owner_user_id
    ORDER BY MIN(upload_items.expires_at), upload_batches.owner_user_id
    LIMIT GREATEST(1, LEAST(candidate_limit, 100))
$$;

REVOKE ALL ON FUNCTION list_temporary_original_cleanup_owners(INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION list_temporary_original_cleanup_owners(INTEGER) TO placesplates_app;
