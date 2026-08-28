UPDATE posts
SET coordinate_visibility = 'EXACT'
WHERE coordinate_visibility = 'HIDDEN'
  AND place_id IN (
      SELECT id
      FROM places
      WHERE latitude IS NOT NULL
        AND longitude IS NOT NULL
  );
