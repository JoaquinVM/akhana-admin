-- =============================================================================
-- Migration: V7__create_sales_registration_tables.sql
-- Description: Extensión de ventas para carrito, descuentos, anulación y productos rápidos
-- =============================================================================

-- 1. Extensión de tabla sales para soportar detalle comercial, descuentos, cambio y anulación
ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA',
    ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS discount_items_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS global_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS amount_cash NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS amount_qr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS amount_received NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS change_given NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS voided_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);

-- Actualizar ventas existentes previas a la migración
UPDATE sales 
SET subtotal_amount = total_amount,
    amount_cash = CASE WHEN payment_method = 'EFECTIVO' THEN total_amount ELSE 0.00 END,
    amount_qr = CASE WHEN payment_method = 'QR' THEN total_amount ELSE 0.00 END
WHERE subtotal_amount = 0.00 AND total_amount > 0.00;

CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);

-- 2. Tabla de detalle de ítems de la venta
CREATE TABLE IF NOT EXISTS sale_items (
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

CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(product_id);

-- 3. Tabla de grupos de productos rápidos configurables para el POS
CREATE TABLE IF NOT EXISTS quick_product_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quick_product_groups_order ON quick_product_groups(display_order ASC);

-- 4. Tabla de asignación de productos a grupos rápidos con orden drag-and-drop
CREATE TABLE IF NOT EXISTS quick_product_group_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES quick_product_groups(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quick_group_product UNIQUE (group_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_quick_group_items_group ON quick_product_group_items(group_id, display_order ASC);
CREATE INDEX IF NOT EXISTS idx_quick_group_items_product ON quick_product_group_items(product_id);
