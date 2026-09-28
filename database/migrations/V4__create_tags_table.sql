-- =============================================================================
-- Migration: V4__create_tags_table.sql
-- Description: Creación de la tabla tags para gestión de etiquetas con auditoría
-- Database: PostgreSQL 17
-- =============================================================================

CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    color VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Índice único parcial: Unicidad de nombre insensible a mayúsculas exclusivamente entre etiquetas no eliminadas
CREATE UNIQUE INDEX IF NOT EXISTS uq_tags_name_active ON tags (LOWER(name)) WHERE status != 'ELIMINADO';

-- Índices de consulta frecuente para filtros y ordenación
CREATE INDEX IF NOT EXISTS idx_tags_status ON tags(status);
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
