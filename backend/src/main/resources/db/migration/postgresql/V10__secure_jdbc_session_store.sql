GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE spring_session TO placesplates_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE spring_session_attributes TO placesplates_app;

REVOKE ALL PRIVILEGES ON TABLE spring_session FROM PUBLIC;
REVOKE ALL PRIVILEGES ON TABLE spring_session_attributes FROM PUBLIC;

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
                'REVOKE ALL PRIVILEGES ON TABLE spring_session FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'REVOKE ALL PRIVILEGES ON TABLE spring_session_attributes FROM %I',
                exposed_role
            );
        END IF;
    END LOOP;
END
$$;
