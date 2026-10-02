-- =============================================================================
-- Migration: V6__create_cash_management_tables.sql
-- Description: Creación de tablas de sesiones de caja compartida, cortes y ventas
-- =============================================================================

CREATE TABLE IF NOT EXISTS cash_sessions (
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

-- Restricción única parcial: SOLO UNA caja ABIERTA simultáneamente a nivel de BD
CREATE UNIQUE INDEX IF NOT EXISTS idx_cash_sessions_only_one_open 
ON cash_sessions (status) 
WHERE status = 'ABIERTA';

CREATE INDEX IF NOT EXISTS idx_cash_sessions_status ON cash_sessions(status);
CREATE INDEX IF NOT EXISTS idx_cash_sessions_opened_at ON cash_sessions(opened_at DESC);

-- Tabla de cortes de denominación para arqueo de cierre
CREATE TABLE IF NOT EXISTS cash_denomination_cuts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id) ON DELETE CASCADE,
    denomination NUMERIC(6, 2) NOT NULL,
    cash_quantity INT NOT NULL DEFAULT 0,
    reserve_quantity INT NOT NULL DEFAULT 0,
    subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0.00
);

CREATE INDEX IF NOT EXISTS idx_cash_cuts_session ON cash_denomination_cuts(cash_session_id);

-- Tabla de ventas vinculadas a la sesión de caja
CREATE TABLE IF NOT EXISTS sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_number VARCHAR(50) NOT NULL,
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    total_amount NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL, -- 'EFECTIVO', 'QR'
    description VARCHAR(255),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_cash_session ON sales(cash_session_id);
CREATE INDEX IF NOT EXISTS idx_sales_created_at ON sales(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sales_payment_method ON sales(payment_method);
