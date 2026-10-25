-- =============================================================================
-- Migration: V9__create_user_management_and_permissions_tables.sql
-- Description: Gestión de usuarios, permisos de acceso por submenú y auditoría
-- =============================================================================

-- 1. Extensión de columnas en la tabla app_users
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- 2. Inicialización de datos para usuarios preexistentes
UPDATE app_users SET full_name = 'Administrador General' WHERE username = 'admin' AND full_name IS NULL;
UPDATE app_users SET full_name = 'Vendedor' WHERE username = 'seller' AND full_name IS NULL;
UPDATE app_users SET full_name = username WHERE full_name IS NULL;
UPDATE app_users SET created_by = 'SYSTEM' WHERE created_by IS NULL;

-- 3. Restricción NOT NULL para full_name una vez poblados los datos existentes
ALTER TABLE app_users ALTER COLUMN full_name SET NOT NULL;

-- 4. Índices para auditoría y búsqueda
CREATE INDEX IF NOT EXISTS idx_app_users_deleted_at ON app_users(deleted_at);
CREATE INDEX IF NOT EXISTS idx_app_users_full_name ON app_users(full_name);

-- 5. Tabla de permisos por usuario y submenú
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, permission_key)
);

CREATE INDEX IF NOT EXISTS idx_user_permissions_user_id ON user_permissions(user_id);

-- 6. Asignación inicial de permisos a todos los usuarios preexistentes
-- Garantiza acceso completo e idempotente a todos los submenús del sistema
INSERT INTO user_permissions (user_id, permission_key)
SELECT u.id, p.perm
FROM app_users u
CROSS JOIN (
    VALUES
        ('SALES_POS'),
        ('SALES_HISTORY'),
        ('SALES_CASH_HISTORY'),
        ('FINANCES_SUMMARY'),
        ('FINANCES_TRANSACTIONS'),
        ('FINANCES_ACCOUNTS'),
        ('FINANCES_CATEGORIES'),
        ('CATALOG_PRODUCTS'),
        ('CATALOG_CATEGORIES'),
        ('CATALOG_TAGS'),
        ('PURCHASES_SUPPLIERS'),
        ('SECURITY_USERS')
) AS p(perm)
ON CONFLICT (user_id, permission_key) DO NOTHING;
