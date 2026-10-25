-- COM-01 to COM-06: purchases (orders) with lines, shipping paid as a Transporte expense (P-24),
-- payments from Caja or another account, and audited cost/price changes from a purchase line (COM-03).

-- -----------------------------------------------------------------------------
-- System expense categories the code needs, like Ventas (T-17): Pedido and Transporte (FIN-05).
-- Existing categories with those names are reused.
-- -----------------------------------------------------------------------------
ALTER TABLE financial_categories ADD COLUMN system_key VARCHAR(30);
ALTER TABLE financial_categories ADD CONSTRAINT chk_financial_categories_system_key
    CHECK (system_key IN ('PURCHASE_PAYMENT', 'PURCHASE_SHIPPING'));
CREATE UNIQUE INDEX uq_financial_categories_system_key ON financial_categories (system_key)
    WHERE system_key IS NOT NULL AND deleted_at IS NULL;

UPDATE financial_categories SET system_key = 'PURCHASE_PAYMENT'
WHERE id = (SELECT id FROM financial_categories
            WHERE LOWER(name) = 'pedido' AND type = 'GASTO' AND deleted_at IS NULL
            ORDER BY created_at LIMIT 1);
INSERT INTO financial_categories (name, type, color, system_key)
SELECT 'Pedido', 'GASTO', '#8A5A2B', 'PURCHASE_PAYMENT'
WHERE NOT EXISTS (SELECT 1 FROM financial_categories WHERE system_key = 'PURCHASE_PAYMENT');

UPDATE financial_categories SET system_key = 'PURCHASE_SHIPPING'
WHERE id = (SELECT id FROM financial_categories
            WHERE LOWER(name) = 'transporte' AND type = 'GASTO' AND deleted_at IS NULL
            ORDER BY created_at LIMIT 1);
INSERT INTO financial_categories (name, type, color, system_key)
SELECT 'Transporte', 'GASTO', '#3B6EA5', 'PURCHASE_SHIPPING'
WHERE NOT EXISTS (SELECT 1 FROM financial_categories WHERE system_key = 'PURCHASE_SHIPPING');

-- -----------------------------------------------------------------------------
-- Purchases
-- -----------------------------------------------------------------------------
CREATE SEQUENCE purchase_number_seq START 1;

CREATE TABLE purchases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_number VARCHAR(50) NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    purchase_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO' CHECK (status IN ('REGISTRADO', 'ANULADO')),
    items_total NUMERIC(12, 2) NOT NULL CHECK (items_total >= 0),
    shipping_cost NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (shipping_cost >= 0),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    consignment BOOLEAN NOT NULL DEFAULT FALSE,
    comment VARCHAR(500),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE UNIQUE INDEX uq_purchases_purchase_number ON purchases (purchase_number);
CREATE INDEX idx_purchases_supplier ON purchases(supplier_id);
CREATE INDEX idx_purchases_date ON purchases(purchase_date DESC);

CREATE TABLE purchase_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    product_id UUID NOT NULL REFERENCES products(id),
    product_name VARCHAR(150) NOT NULL,
    product_code VARCHAR(50),
    -- Up to 3 decimals for products sold by weight (CAT-08)
    quantity NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    -- Product cost when the purchase was registered, to show the cost alert afterwards (COM-03)
    previous_unit_cost NUMERIC(12, 2) NOT NULL,
    cost_updated BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_purchase_items_purchase ON purchase_items(purchase_id);
CREATE INDEX idx_purchase_items_product ON purchase_items(product_id);

CREATE TABLE purchase_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    account_id UUID NOT NULL REFERENCES financial_accounts(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    payment_date TIMESTAMP WITH TIME ZONE NOT NULL,
    financial_transaction_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'ANULADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE INDEX idx_purchase_payments_purchase ON purchase_payments(purchase_id);

-- Payment and shipping movements point to their purchase (P-24, COM-06)
ALTER TABLE financial_transactions ADD COLUMN purchase_id UUID REFERENCES purchases(id);
CREATE INDEX idx_financial_tx_purchase ON financial_transactions(purchase_id);

ALTER TABLE purchase_payments ADD CONSTRAINT fk_purchase_payments_transaction
    FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id);

-- -----------------------------------------------------------------------------
-- Audit of product cost and sell price changes (CAT-07, COM-03)
-- -----------------------------------------------------------------------------
CREATE TABLE product_price_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    previous_buy_price NUMERIC(12, 2) NOT NULL,
    new_buy_price NUMERIC(12, 2) NOT NULL,
    previous_sell_price NUMERIC(12, 2) NOT NULL,
    new_sell_price NUMERIC(12, 2) NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('PURCHASE', 'CATALOG')),
    purchase_id UUID REFERENCES purchases(id),
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_price_changes_product ON product_price_changes(product_id, changed_at DESC);
