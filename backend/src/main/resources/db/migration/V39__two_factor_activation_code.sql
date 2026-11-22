-- 098: a one-time code, handed in person by the administrator, is needed to activate the second factor of a new
-- user or of one whose second factor was removed. Only its hash is kept, with its expiry (24 h). The users that
-- already exist keep activating without a code (activation_code_required = false)
ALTER TABLE app_users ADD COLUMN activation_code_hash VARCHAR(100);
ALTER TABLE app_users ADD COLUMN activation_code_expires_at TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN activation_code_required BOOLEAN NOT NULL DEFAULT FALSE;
