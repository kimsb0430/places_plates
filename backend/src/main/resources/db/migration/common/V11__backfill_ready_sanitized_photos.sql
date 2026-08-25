UPDATE photos
SET processing_status = 'READY',
    updated_at = CURRENT_TIMESTAMP
WHERE processing_status = 'PROCESSING'
  AND EXISTS (
      SELECT 1
      FROM upload_items
      JOIN image_processing_jobs
        ON image_processing_jobs.upload_item_id = upload_items.id
      JOIN photo_assets
        ON photo_assets.photo_id = photos.id
      WHERE upload_items.result_photo_id = photos.id
        AND image_processing_jobs.status = 'COMPLETED'
        AND photo_assets.variant_type = 'SANITIZED_MASTER'
        AND photo_assets.access_level = 'PRIVATE'
        AND photo_assets.metadata_scan_passed = TRUE
  );
