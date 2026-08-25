ALTER TABLE photo_assets
    ADD COLUMN watermark_position VARCHAR(30);

ALTER TABLE photo_assets
    ADD CONSTRAINT ck_photo_assets_watermark_state CHECK (
        (
            watermark_applied = FALSE
            AND watermark_version IS NULL
            AND watermark_position IS NULL
        )
        OR (
            watermark_applied = TRUE
            AND variant_type <> 'SANITIZED_MASTER'
            AND watermark_version IS NOT NULL
            AND watermark_position IN ('BOTTOM_RIGHT')
        )
    );
