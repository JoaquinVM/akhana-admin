-- USR-06, T-19: tokens issued before the last password change are rejected, so changing the password closes the
-- other open sessions of that user. Null means the password was never changed (every token stays valid).
ALTER TABLE app_users ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;
