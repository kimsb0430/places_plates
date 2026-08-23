ALTER TABLE app_users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'MEMBER'));
