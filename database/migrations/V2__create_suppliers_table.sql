-- =============================================================================
-- Migration: V2__create_suppliers_table.sql
-- Description: Creación de tabla suppliers para gestión de proveedores y auditoría
-- Database: PostgreSQL 17
-- =============================================================================

CREATE TABLE IF NOT EXISTS suppliers (
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

-- Índices únicos parciales (excluyen registros con estado ELIMINADO)
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_name_active ON suppliers (LOWER(name)) WHERE status != 'ELIMINADO';
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_code_active ON suppliers (LOWER(code)) WHERE status != 'ELIMINADO';

CREATE INDEX IF NOT EXISTS idx_suppliers_status ON suppliers(status);
CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers(name);
CREATE INDEX IF NOT EXISTS idx_suppliers_code ON suppliers(code);
