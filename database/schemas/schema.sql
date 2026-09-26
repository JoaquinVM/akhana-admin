-- =============================================================================
-- Canonical Schema Definition: database/schemas/schema.sql
-- Description: Estado canónico actual del esquema de la base de datos Akhana
-- Actualizado tras: V1__initial_setup.sql
-- =============================================================================

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'SELLER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_suppliers_name_active ON suppliers (LOWER(name)) WHERE status != 'ELIMINADO';
CREATE UNIQUE INDEX uq_suppliers_code_active ON suppliers (LOWER(code)) WHERE status != 'ELIMINADO';
