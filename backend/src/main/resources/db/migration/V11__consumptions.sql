-- CON-01: internal consumptions. Products used or eaten in the store, valued at the product cost of the moment.
-- They move no money: no financial movement and no cash session.

CREATE SEQUENCE consumption_number_seq START 1;

CREATE TABLE consumptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumption_number VARCHAR(50) NOT NULL,
    consumption_date DATE NOT NULL,
    -- Who consumed it, free text (P-36)
    consumed_for VARCHAR(150),
    comment VARCHAR(500),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO' CHECK (status IN ('REGISTRADO', 'ANULADO')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_by VARCHAR(100),
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500)
);

CREATE UNIQUE INDEX uq_consumptions_consumption_number ON consumptions (consumption_number);
CREATE INDEX idx_consumptions_date ON consumptions(consumption_date DESC);

CREATE TABLE consumption_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumption_id UUID NOT NULL REFERENCES consumptions(id),
    product_id UUID NOT NULL REFERENCES products(id),
    product_name VARCHAR(150) NOT NULL,
    product_code VARCHAR(50),
    -- Up to 3 decimals for products sold by weight (CAT-08)
    quantity NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    -- Copy of the product buy_price when the consumption was registered (like POS-05)
    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_consumption_items_consumption ON consumption_items(consumption_id);
CREATE INDEX idx_consumption_items_product ON consumption_items(product_id);
