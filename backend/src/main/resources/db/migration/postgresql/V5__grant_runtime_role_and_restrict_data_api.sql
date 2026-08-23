DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'placesplates_app'
    ) THEN
        RAISE EXCEPTION 'Required runtime role placesplates_app does not exist';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO placesplates_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO placesplates_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO placesplates_app;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO placesplates_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO placesplates_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO placesplates_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO placesplates_app;

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
                'REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'REVOKE ALL PRIVILEGES ON SCHEMA public FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I',
                exposed_role
            );
            EXECUTE FORMAT(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I',
                exposed_role
            );
        END IF;
    END LOOP;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.schemata
        WHERE schema_name = 'extensions'
    ) THEN
        GRANT USAGE ON SCHEMA extensions TO placesplates_app;
    END IF;
END
$$;
