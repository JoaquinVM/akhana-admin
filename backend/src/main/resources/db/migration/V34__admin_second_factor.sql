-- Second factor of the administrators (USR-08, specs/users.md) before Admin goes on the internet (specs/deploy.md).
ALTER TABLE app_users ADD COLUMN totp_secret VARCHAR(64);
ALTER TABLE app_users ADD COLUMN totp_enabled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_users ADD COLUMN totp_last_step BIGINT;

-- The passwords were chosen when only Tailscale reached the app: every active user changes it at the next login.
-- The open sessions end too (T-19), or they would keep working without going through the change.
UPDATE app_users
   SET must_change_password = TRUE,
       password_changed_at = now()
 WHERE deleted_at IS NULL
   AND active = TRUE;
