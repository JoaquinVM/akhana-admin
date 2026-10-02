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

CREATE TABLE categories (
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

CREATE UNIQUE INDEX uq_categories_name_active ON categories (LOWER(name)) WHERE status != 'ELIMINADO';
CREATE INDEX idx_categories_status ON categories(status);
CREATE INDEX idx_categories_name ON categories(name);

CREATE TABLE tags (
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

CREATE UNIQUE INDEX uq_tags_name_active ON tags (LOWER(name)) WHERE status != 'ELIMINADO';
CREATE INDEX idx_tags_status ON tags(status);
CREATE INDEX idx_tags_name ON tags(name);

CREATE TABLE products (
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

CREATE UNIQUE INDEX uq_products_code_active ON products (LOWER(code)) WHERE status != 'ELIMINADO';
CREATE UNIQUE INDEX uq_products_name_active ON products (LOWER(name)) WHERE status != 'ELIMINADO';
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_supplier ON products(supplier_id);
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_code ON products(code);

CREATE TABLE product_tags (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, tag_id)
);

CREATE INDEX idx_product_tags_tag_id ON product_tags(tag_id);

-- =============================================================================
-- GESTIÓN DE CAJA COMPARTIDA Y VENTAS
-- =============================================================================

CREATE TABLE cash_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_number SERIAL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    opening_amount NUMERIC(12, 2) NOT NULL,
    opening_comment VARCHAR(500),
    opened_by VARCHAR(100) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closing_amount NUMERIC(12, 2),
    closing_comment VARCHAR(500),
    closed_by VARCHAR(100),
    closed_at TIMESTAMP WITH TIME ZONE,
    total_sales_cash NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_sales_qr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_sales NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    expected_cash NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    difference NUMERIC(12, 2)
);

CREATE UNIQUE INDEX idx_cash_sessions_only_one_open ON cash_sessions (status) WHERE status = 'ABIERTA';
CREATE INDEX idx_cash_sessions_status ON cash_sessions(status);
CREATE INDEX idx_cash_sessions_opened_at ON cash_sessions(opened_at DESC);

CREATE TABLE cash_denomination_cuts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id) ON DELETE CASCADE,
    denomination NUMERIC(6, 2) NOT NULL,
    cash_quantity INT NOT NULL DEFAULT 0,
    reserve_quantity INT NOT NULL DEFAULT 0,
    subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0.00
);

CREATE INDEX idx_cash_cuts_session ON cash_denomination_cuts(cash_session_id);

CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_number VARCHAR(50) NOT NULL,
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA',
    subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    discount_items_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    global_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL, -- 'EFECTIVO', 'QR', 'MIXTO'
    amount_cash NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    amount_qr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    amount_received NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    change_given NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    description VARCHAR(255),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_at TIMESTAMP WITH TIME ZONE,
    voided_by VARCHAR(100),
    void_reason VARCHAR(500)
);

CREATE INDEX idx_sales_cash_session ON sales(cash_session_id);
CREATE INDEX idx_sales_status ON sales(status);
CREATE INDEX idx_sales_created_at ON sales(created_at DESC);
CREATE INDEX idx_sales_payment_method ON sales(payment_method);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    product_name VARCHAR(150) NOT NULL,
    product_code VARCHAR(50),
    unit_price NUMERIC(12, 2) NOT NULL,
    discount_per_unit NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    final_unit_price NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_items(product_id);

CREATE TABLE quick_product_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quick_product_groups_order ON quick_product_groups(display_order ASC);

CREATE TABLE quick_product_group_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES quick_product_groups(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quick_group_product UNIQUE (group_id, product_id)
);

CREATE INDEX idx_quick_group_items_group ON quick_product_group_items(group_id, display_order ASC);
CREATE INDEX idx_quick_group_items_product ON quick_product_group_items(product_id);



