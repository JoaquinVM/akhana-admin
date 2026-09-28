-- =============================================================================
-- Migration: V3__create_categories_table.sql
-- Description: Creación de tabla categories para gestión de categorías de catálogo
-- Database: PostgreSQL 17
-- =============================================================================

CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    color VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Índice único parcial sobre el nombre (excluye registros con estado ELIMINADO)
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_name_active ON categories (LOWER(name)) WHERE status != 'ELIMINADO';

CREATE INDEX IF NOT EXISTS idx_categories_status ON categories(status);
CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name);
