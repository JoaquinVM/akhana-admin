-- CAT-11: presentations of products sold by weight (1/2 libra = 0,227 kg) with an optional fixed price.
-- The cost is never stored: it is weight x the product cost per kilo.
CREATE TABLE product_presentations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    name VARCHAR(50) NOT NULL,
    weight_kg NUMERIC(12, 3) NOT NULL CHECK (weight_kg > 0),
    -- Null: the price is weight x the product price per kilo
    fixed_price NUMERIC(12, 2) CHECK (fixed_price > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_product_presentations_product ON product_presentations(product_id);
CREATE UNIQUE INDEX uq_product_presentations_name ON product_presentations(product_id, lower(name)) WHERE active;
CREATE UNIQUE INDEX uq_product_presentations_weight ON product_presentations(product_id, weight_kg) WHERE active;

-- A line sold or consumed by presentation: quantity is the number of packs and weight_kg the kilos
-- (quantity x presentation weight) used by the reports; the four columns go together
ALTER TABLE sale_items
    ADD COLUMN presentation_id UUID REFERENCES product_presentations(id),
    ADD COLUMN presentation_name VARCHAR(50),
    ADD COLUMN presentation_weight_kg NUMERIC(12, 3),
    ADD COLUMN weight_kg NUMERIC(12, 3),
    ADD CONSTRAINT chk_sale_items_presentation CHECK (
        (presentation_id IS NULL AND presentation_name IS NULL AND presentation_weight_kg IS NULL AND weight_kg IS NULL)
        OR (presentation_id IS NOT NULL AND presentation_name IS NOT NULL
            AND presentation_weight_kg > 0 AND weight_kg > 0));

ALTER TABLE consumption_items
    ADD COLUMN presentation_id UUID REFERENCES product_presentations(id),
    ADD COLUMN presentation_name VARCHAR(50),
    ADD COLUMN presentation_weight_kg NUMERIC(12, 3),
    ADD COLUMN weight_kg NUMERIC(12, 3),
    ADD CONSTRAINT chk_consumption_items_presentation CHECK (
        (presentation_id IS NULL AND presentation_name IS NULL AND presentation_weight_kg IS NULL AND weight_kg IS NULL)
        OR (presentation_id IS NOT NULL AND presentation_name IS NOT NULL
            AND presentation_weight_kg > 0 AND weight_kg > 0));

-- Fixed price changes of a presentation (CAT-11, CAT-07); null = no fixed price
ALTER TABLE product_price_changes
    ADD COLUMN presentation_id UUID REFERENCES product_presentations(id),
    ADD COLUMN previous_fixed_price NUMERIC(12, 2),
    ADD COLUMN new_fixed_price NUMERIC(12, 2);

-- The fixed 100 g and 1/2 libra buttons of the POS (POS-10) become presentations of every product by weight
INSERT INTO product_presentations (product_id, name, weight_kg, created_by)
SELECT p.id, v.name, v.weight_kg, 'SYSTEM'
FROM products p
CROSS JOIN (VALUES ('100 g', 0.100), ('1/2 libra', 0.227)) AS v(name, weight_kg)
WHERE p.sale_unit = 'KILOGRAM' AND p.status <> 'ELIMINADO';
