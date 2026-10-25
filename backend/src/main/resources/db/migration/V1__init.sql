-- =============================================================================
-- V1__init.sql
-- Esquema inicial consolidado (reemplaza database/migrations V1–V9).
-- Refleja las entidades JPA; Hibernate solo valida (ddl-auto=validate).
-- Database: PostgreSQL 17 (gen_random_uuid() es nativo)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Usuarios y permisos
-- -----------------------------------------------------------------------------
CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'SELLER' CHECK (role IN ('ADMIN', 'SELLER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_app_users_deleted_at ON app_users(deleted_at);
CREATE INDEX idx_app_users_full_name ON app_users(full_name);

CREATE TABLE user_permissions (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, permission_key)
);

-- -----------------------------------------------------------------------------
-- Catálogo: proveedores, categorías, etiquetas y productos
-- -----------------------------------------------------------------------------
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'INACTIVO', 'ELIMINADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Unicidad sin distinguir mayúsculas, excluyendo eliminados
CREATE UNIQUE INDEX uq_suppliers_name_active ON suppliers (LOWER(name)) WHERE status <> 'ELIMINADO';
CREATE UNIQUE INDEX uq_suppliers_code_active ON suppliers (LOWER(code)) WHERE status <> 'ELIMINADO';
CREATE INDEX idx_suppliers_status ON suppliers(status);
CREATE INDEX idx_suppliers_name ON suppliers(name);
CREATE INDEX idx_suppliers_code ON suppliers(code);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    color VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ELIMINADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_categories_name_active ON categories (LOWER(name)) WHERE status <> 'ELIMINADO';
CREATE INDEX idx_categories_status ON categories(status);
CREATE INDEX idx_categories_name ON categories(name);

CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    color VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ELIMINADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_tags_name_active ON tags (LOWER(name)) WHERE status <> 'ELIMINADO';
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
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'INACTIVO', 'ELIMINADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_products_code_active ON products (LOWER(code)) WHERE status <> 'ELIMINADO';
CREATE UNIQUE INDEX uq_products_name_active ON products (LOWER(name)) WHERE status <> 'ELIMINADO';
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

-- -----------------------------------------------------------------------------
-- Caja
-- -----------------------------------------------------------------------------
CREATE TABLE cash_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_number BIGINT GENERATED BY DEFAULT AS IDENTITY UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ABIERTA' CHECK (status IN ('ABIERTA', 'CERRADA')),
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

-- Una sola caja ABIERTA a la vez
CREATE UNIQUE INDEX idx_cash_sessions_only_one_open ON cash_sessions (status) WHERE status = 'ABIERTA';
CREATE INDEX idx_cash_sessions_opened_at ON cash_sessions(opened_at DESC);

CREATE TABLE cash_denomination_cuts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    denomination NUMERIC(6, 2) NOT NULL,
    cash_quantity INT NOT NULL DEFAULT 0,
    reserve_quantity INT NOT NULL DEFAULT 0,
    subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0.00
);

CREATE INDEX idx_cash_cuts_session ON cash_denomination_cuts(cash_session_id);

-- -----------------------------------------------------------------------------
-- Ventas
-- -----------------------------------------------------------------------------
CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_number VARCHAR(50) NOT NULL,
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA' CHECK (status IN ('COMPLETADA', 'ANULADA')),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('EFECTIVO', 'QR', 'MIXTO')),
    subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    discount_items_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    global_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(12, 2) NOT NULL,
    amount_cash NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    amount_qr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    amount_received NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    change_given NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    description VARCHAR(255),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE INDEX idx_sales_cash_session ON sales(cash_session_id);
CREATE INDEX idx_sales_created_at ON sales(created_at DESC);
CREATE INDEX idx_sales_payment_method ON sales(payment_method);
CREATE INDEX idx_sales_status ON sales(status);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES sales(id),
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

-- Productos rápidos del POS (configuración)
CREATE TABLE quick_product_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quick_product_groups_order ON quick_product_groups(display_order);

CREATE TABLE quick_product_group_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES quick_product_groups(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quick_group_product UNIQUE (group_id, product_id)
);

CREATE INDEX idx_quick_group_items_group ON quick_product_group_items(group_id, display_order);
CREATE INDEX idx_quick_group_items_product ON quick_product_group_items(product_id);

-- -----------------------------------------------------------------------------
-- Finanzas
-- -----------------------------------------------------------------------------
CREATE TABLE financial_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    color VARCHAR(50) NOT NULL DEFAULT '#2E5B27',
    initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    is_cash_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_qr_account BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Una sola cuenta Caja y una sola cuenta QR activa
CREATE UNIQUE INDEX idx_financial_accounts_only_one_cash ON financial_accounts (is_cash_account) WHERE is_cash_account = TRUE;
CREATE UNIQUE INDEX idx_financial_accounts_only_one_qr ON financial_accounts (is_qr_account) WHERE is_qr_account = TRUE AND deleted_at IS NULL;
CREATE INDEX idx_financial_accounts_deleted_at ON financial_accounts(deleted_at);

CREATE TABLE financial_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('INGRESO', 'GASTO')),
    color VARCHAR(50) NOT NULL DEFAULT '#2E5B27',
    is_sales_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Una sola categoría predeterminada de Ventas activa
CREATE UNIQUE INDEX idx_financial_categories_only_one_sales ON financial_categories (is_sales_default) WHERE is_sales_default = TRUE AND deleted_at IS NULL;
CREATE INDEX idx_financial_categories_type ON financial_categories(type);
CREATE INDEX idx_financial_categories_deleted_at ON financial_categories(deleted_at);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(30) NOT NULL CHECK (type IN ('INGRESO_MANUAL', 'GASTO_MANUAL', 'TRANSFERENCIA', 'SALDO_INICIAL', 'VENTA_INGRESO')),
    source_account_id UUID NOT NULL REFERENCES financial_accounts(id),
    destination_account_id UUID REFERENCES financial_accounts(id),
    category_id UUID REFERENCES financial_categories(id),
    amount NUMERIC(14, 2) NOT NULL,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ANULADO')),
    sale_id UUID REFERENCES sales(id),
    cash_session_id UUID REFERENCES cash_sessions(id),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE INDEX idx_financial_tx_source_account ON financial_transactions(source_account_id);
CREATE INDEX idx_financial_tx_dest_account ON financial_transactions(destination_account_id);
CREATE INDEX idx_financial_tx_category ON financial_transactions(category_id);
CREATE INDEX idx_financial_tx_date ON financial_transactions(transaction_date DESC);
CREATE INDEX idx_financial_tx_status ON financial_transactions(status);
CREATE INDEX idx_financial_tx_type ON financial_transactions(type);
CREATE INDEX idx_financial_tx_sale ON financial_transactions(sale_id);
CREATE INDEX idx_financial_tx_cash_session ON financial_transactions(cash_session_id);

-- -----------------------------------------------------------------------------
-- Filas de sistema que el código necesita (las cuentas del negocio, como Bisa
-- y Efectivo, se cargan en la migración de datos: T-10)
-- -----------------------------------------------------------------------------
INSERT INTO financial_categories (name, type, color, is_sales_default)
VALUES ('Ventas', 'INGRESO', '#2E5B27', TRUE);

WITH caja AS (
    INSERT INTO financial_accounts (name, color, is_cash_account)
    VALUES ('Caja', '#2E5B27', TRUE)
    RETURNING id
)
INSERT INTO financial_transactions (type, source_account_id, amount, transaction_date, description, created_by)
SELECT 'SALDO_INICIAL', id, 0.00, CURRENT_TIMESTAMP, 'Saldo inicial de apertura', 'SYSTEM' FROM caja;
