-- CAT-05: each product says whether it controls stock; the special products (free lines, third parties) never do
ALTER TABLE products ADD COLUMN stock_controlled BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE products SET stock_controlled = FALSE WHERE special_type IS NOT NULL;

-- COM-03 with INV-07 (option A of Joaquín): the supplier's last price per base unit, which the purchase alert compares
-- and the line proposes; buy_price is the last cost placed in the store, with its part of the shipping, which the
-- suggested price uses. Null until a purchase updates the cost: until then the alert compares buy_price, as in the V1
ALTER TABLE products ADD COLUMN last_purchase_cost NUMERIC(12, 2);

-- INV-01, T-04: the stock of a product, a shortcut of its ledger (stock_movements) kept in the same transaction as each
-- movement. A row with started_at NULL is a product still to be counted (INV-18): it does not move stock yet.
-- Kept apart from products so that saving a product never writes over its stock
CREATE TABLE product_stocks (
    product_id UUID PRIMARY KEY REFERENCES products(id),
    quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    stock_value NUMERIC(14, 2) NOT NULL DEFAULT 0,
    average_cost NUMERIC(14, 4),
    started_at TIMESTAMP WITH TIME ZONE
);

-- INV-05: a count, by category or by product, blind; it generates its adjustments when it is confirmed
CREATE SEQUENCE stock_count_number_seq START 1;
CREATE TABLE stock_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    count_number BIGINT NOT NULL UNIQUE,
    category_id UUID REFERENCES categories(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ABIERTO', 'CONFIRMADO', 'DESCARTADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_by VARCHAR(100),
    closed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_stock_counts_closed CHECK ((status = 'ABIERTO') = (closed_at IS NULL))
);

-- What was counted of one product, in base units, with the packages and loose units as written («3 Caja x12 + 4»).
-- expected_quantity is the stock when the quantity was written, never shown while counting (blind)
CREATE TABLE stock_count_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_count_id UUID NOT NULL REFERENCES stock_counts(id),
    product_id UUID NOT NULL REFERENCES products(id),
    counted_quantity NUMERIC(14, 3) NOT NULL CHECK (counted_quantity >= 0),
    breakdown VARCHAR(300) NOT NULL,
    expected_quantity NUMERIC(14, 3) NOT NULL,
    initial_load BOOLEAN NOT NULL,
    -- The packages ({package id: whole packs}) and loose base units as saved, so counting again adds to them
    counted_packages JSONB NOT NULL DEFAULT '{}',
    counted_loose NUMERIC(14, 3) NOT NULL DEFAULT 0,
    counted_by VARCHAR(100) NOT NULL,
    counted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_stock_count_lines_product UNIQUE (stock_count_id, product_id)
);

-- INV-02, INV-03: the valued ledger. quantity and stock_value are signed; the balance is the sum of the rows of the
-- product (T-04). expensed_value is what went to the cost of sales instead of the stock (INV-07, no stock left).
-- The date is always the server's (INV-08); seq orders the rows of the same instant
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    product_id UUID NOT NULL REFERENCES products(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('INITIAL_LOAD', 'PURCHASE', 'PURCHASE_REVERSAL', 'PURCHASE_CORRECTION',
        'SALE', 'AT_COST_SALE', 'CONSUMPTION', 'SALE_VOID', 'CONSUMPTION_VOID', 'COUNT_ADJUSTMENT', 'ADJUSTMENT',
        'AVERAGE_RESET')),
    quantity NUMERIC(14, 3) NOT NULL,
    unit_cost NUMERIC(14, 4),
    stock_value NUMERIC(14, 2) NOT NULL,
    expensed_value NUMERIC(14, 2) NOT NULL DEFAULT 0,
    balance_quantity NUMERIC(14, 3) NOT NULL,
    balance_value NUMERIC(14, 2) NOT NULL,
    balance_average_cost NUMERIC(14, 4),
    reference VARCHAR(60),
    reason VARCHAR(20) CHECK (reason IN ('MERMA', 'ROTURA', 'VENCIMIENTO', 'SOBRANTE')),
    comment VARCHAR(255),
    sale_item_id UUID REFERENCES sale_items(id),
    consumption_item_id UUID REFERENCES consumption_items(id),
    purchase_item_id UUID REFERENCES purchase_items(id),
    stock_count_line_id UUID REFERENCES stock_count_lines(id),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_stock_movements_product ON stock_movements(product_id, seq);
CREATE INDEX idx_stock_movements_sale_item ON stock_movements(sale_item_id) WHERE sale_item_id IS NOT NULL;
CREATE INDEX idx_stock_movements_consumption_item ON stock_movements(consumption_item_id) WHERE consumption_item_id IS NOT NULL;
CREATE INDEX idx_stock_movements_purchase_item ON stock_movements(purchase_item_id) WHERE purchase_item_id IS NOT NULL;

-- INV-18: when the purchase was marked delivered (null while pending, and for the ones delivered before this
-- migration). A purchase delivered after a product's first count was written enters with that initial load
ALTER TABLE purchases ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;

-- INV-10: a sale or consumption confirmed with more than the recorded stock
ALTER TABLE sales ADD COLUMN negative_stock_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE consumptions ADD COLUMN negative_stock_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

-- INV-18: every product that already exists waits for its first count, its initial load
INSERT INTO product_stocks (product_id)
SELECT id FROM products WHERE special_type IS NULL;
