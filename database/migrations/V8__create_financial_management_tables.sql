-- =============================================================================
-- Migration: V8__create_financial_management_tables.sql
-- Description: Módulo de Gestión de cuentas y transacciones financieras
-- =============================================================================

-- 1. Tabla de cuentas financieras
CREATE TABLE IF NOT EXISTS financial_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    color VARCHAR(50) NOT NULL DEFAULT '#2E5B27',
    initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    is_cash_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_qr_account BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100)
);

-- Restricción única parcial: Solo una cuenta de cobros QR activa (no eliminada)
CREATE UNIQUE INDEX IF NOT EXISTS idx_financial_accounts_only_one_qr 
ON financial_accounts (is_qr_account) 
WHERE is_qr_account = TRUE AND deleted_at IS NULL;

-- Restricción única parcial: Solo una cuenta de tipo Caja en el sistema
CREATE UNIQUE INDEX IF NOT EXISTS idx_financial_accounts_only_one_cash 
ON financial_accounts (is_cash_account) 
WHERE is_cash_account = TRUE;

CREATE INDEX IF NOT EXISTS idx_financial_accounts_deleted_at ON financial_accounts(deleted_at);

-- 2. Tabla de categorías financieras (Ingresos / Gastos)
CREATE TABLE IF NOT EXISTS financial_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL, -- 'INGRESO', 'GASTO'
    color VARCHAR(50) NOT NULL DEFAULT '#2E5B27',
    is_sales_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100)
);

-- Restricción única parcial: Solo una categoría predeterminada de Ventas activa
CREATE UNIQUE INDEX IF NOT EXISTS idx_financial_categories_only_one_sales 
ON financial_categories (is_sales_default) 
WHERE is_sales_default = TRUE AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_financial_categories_type ON financial_categories(type);
CREATE INDEX IF NOT EXISTS idx_financial_categories_deleted_at ON financial_categories(deleted_at);

-- 3. Tabla de transacciones financieras
CREATE TABLE IF NOT EXISTS financial_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(30) NOT NULL, -- 'INGRESO_MANUAL', 'GASTO_MANUAL', 'TRANSFERENCIA', 'SALDO_INICIAL', 'VENTA_INGRESO'
    source_account_id UUID NOT NULL REFERENCES financial_accounts(id),
    destination_account_id UUID REFERENCES financial_accounts(id),
    category_id UUID REFERENCES financial_categories(id),
    amount NUMERIC(14, 2) NOT NULL,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO', -- 'ACTIVO', 'ANULADO'
    sale_id UUID REFERENCES sales(id),
    cash_session_id UUID REFERENCES cash_sessions(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    voided_by VARCHAR(100),
    void_reason VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_financial_tx_source_account ON financial_transactions(source_account_id);
CREATE INDEX IF NOT EXISTS idx_financial_tx_dest_account ON financial_transactions(destination_account_id);
CREATE INDEX IF NOT EXISTS idx_financial_tx_category ON financial_transactions(category_id);
CREATE INDEX IF NOT EXISTS idx_financial_tx_date ON financial_transactions(transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_financial_tx_status ON financial_transactions(status);
CREATE INDEX IF NOT EXISTS idx_financial_tx_type ON financial_transactions(type);
CREATE INDEX IF NOT EXISTS idx_financial_tx_sale ON financial_transactions(sale_id);
CREATE INDEX IF NOT EXISTS idx_financial_tx_cash_session ON financial_transactions(cash_session_id);

-- 4. Semillas Maestras Iniciales (Cuentas y Categoría de Ventas)
DO $$
DECLARE
    v_caja_id UUID := gen_random_uuid();
    v_bisa_id UUID := gen_random_uuid();
    v_joaco_id UUID := gen_random_uuid();
    v_sales_cat_id UUID := gen_random_uuid();
BEGIN
    -- Sembrar categoría predeterminada 'Ventas'
    IF NOT EXISTS (SELECT 1 FROM financial_categories WHERE is_sales_default = TRUE) THEN
        INSERT INTO financial_categories (id, name, type, color, is_sales_default, created_by)
        VALUES (v_sales_cat_id, 'Ventas', 'INGRESO', '#2E5B27', TRUE, 'SYSTEM');
    END IF;

    -- Sembrar cuenta 'Caja'
    IF NOT EXISTS (SELECT 1 FROM financial_accounts WHERE is_cash_account = TRUE) THEN
        INSERT INTO financial_accounts (id, name, color, initial_balance, current_balance, is_cash_account, is_qr_account, created_by)
        VALUES (v_caja_id, 'Caja', '#2E5B27', 0.00, 0.00, TRUE, FALSE, 'SYSTEM');

        INSERT INTO financial_transactions (
            id, type, source_account_id, amount, transaction_date, description, status, created_by
        ) VALUES (
            gen_random_uuid(), 'SALDO_INICIAL', v_caja_id, 0.00, CURRENT_TIMESTAMP, 'Saldo inicial de apertura', 'ACTIVO', 'SYSTEM'
        );
    END IF;

    -- Sembrar 'Cuenta bancaria BISA' (configurada como cuenta QR por defecto)
    IF NOT EXISTS (SELECT 1 FROM financial_accounts WHERE name = 'Cuenta bancaria BISA') THEN
        INSERT INTO financial_accounts (id, name, color, initial_balance, current_balance, is_cash_account, is_qr_account, created_by)
        VALUES (v_bisa_id, 'Cuenta bancaria BISA', '#1B3B18', 0.00, 0.00, FALSE, TRUE, 'SYSTEM');

        INSERT INTO financial_transactions (
            id, type, source_account_id, amount, transaction_date, description, status, created_by
        ) VALUES (
            gen_random_uuid(), 'SALDO_INICIAL', v_bisa_id, 0.00, CURRENT_TIMESTAMP, 'Saldo inicial de apertura', 'ACTIVO', 'SYSTEM'
        );
    END IF;

    -- Sembrar 'Cuenta Joaco'
    IF NOT EXISTS (SELECT 1 FROM financial_accounts WHERE name = 'Cuenta Joaco') THEN
        INSERT INTO financial_accounts (id, name, color, initial_balance, current_balance, is_cash_account, is_qr_account, created_by)
        VALUES (v_joaco_id, 'Cuenta Joaco', '#F5B800', 0.00, 0.00, FALSE, FALSE, 'SYSTEM');

        INSERT INTO financial_transactions (
            id, type, source_account_id, amount, transaction_date, description, status, created_by
        ) VALUES (
            gen_random_uuid(), 'SALDO_INICIAL', v_joaco_id, 0.00, CURRENT_TIMESTAMP, 'Saldo inicial de apertura', 'ACTIVO', 'SYSTEM'
        );
    END IF;
END $$;
