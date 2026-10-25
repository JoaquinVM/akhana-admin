-- T-08 / USR-05: a single authorization model. Roles OPERATOR and ADMINISTRATOR group the per-action
-- permissions in code (Role.java); the per-user submenu permissions are no longer stored.

ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check;
ALTER TABLE app_users ALTER COLUMN role DROP DEFAULT;

-- Former admins, and anyone who could manage users, become administrators; everyone else, operators.
UPDATE app_users u
SET role = CASE
    WHEN u.role = 'ADMIN'
      OR EXISTS (SELECT 1 FROM user_permissions p WHERE p.user_id = u.id AND p.permission_key = 'SECURITY_USERS')
    THEN 'ADMINISTRATOR'
    ELSE 'OPERATOR'
END;

ALTER TABLE app_users ALTER COLUMN role SET DEFAULT 'OPERATOR';
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check CHECK (role IN ('OPERATOR', 'ADMINISTRATOR'));

DROP TABLE user_permissions;
