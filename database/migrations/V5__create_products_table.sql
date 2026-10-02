-- =============================================================================
-- Migration: V5__create_products_table.sql
-- Description: Creación de la tabla de productos y tabla intermedia de etiquetas
-- =============================================================================

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    description VARCHAR(500),
    buy_price NUMERIC(12, 2) NOT NULL,
    sell_price NUMERIC(12, 2) NOT NULL,
    fixed_profit NUMERIC(12, 2) NOT NULL,
    percentage_profit NUMERIC(8, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Índices únicos parciales (unicidad entre ACTIVO e INACTIVO, excluyendo ELIMINADO)
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_code_active ON products (LOWER(code)) WHERE status != 'ELIMINADO';
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_name_active ON products (LOWER(name)) WHERE status != 'ELIMINADO';

-- Índices para optimización de consultas, ordenamiento y relaciones
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_supplier ON products(supplier_id);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_code ON products(code);

-- Tabla de unión Many-to-Many con Etiquetas
CREATE TABLE IF NOT EXISTS product_tags (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_product_tags_tag_id ON product_tags(tag_id);
