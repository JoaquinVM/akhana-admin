-- T-03: the first admin (and any password reset in the future) must change the password on first login.
ALTER TABLE app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
