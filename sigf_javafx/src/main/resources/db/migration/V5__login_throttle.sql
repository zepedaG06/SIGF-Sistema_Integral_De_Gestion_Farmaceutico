ALTER TABLE app_user ADD COLUMN failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0);
ALTER TABLE app_user ADD COLUMN locked_until timestamptz;
