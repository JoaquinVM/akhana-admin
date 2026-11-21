-- CAT-15: the presentations of CAT-11 become packages of any product, by unit or by kilo. A package says how many base
-- units it holds (Caja x12 = 12, Bolsa 250 g = 0,250 kg), whether it is sold whole and, optionally, its own price.
-- The existing presentations are sold and keep their weight as the base quantity.
ALTER TABLE product_presentations RENAME COLUMN weight_kg TO base_quantity;
ALTER TABLE product_presentations ADD COLUMN sold BOOLEAN NOT NULL DEFAULT TRUE;
ALTER INDEX uq_product_presentations_weight RENAME TO uq_product_presentations_base_quantity;

-- A line by package keeps the package's base quantity and its own: packs x package base quantity, what reports sum
ALTER TABLE sale_items RENAME COLUMN presentation_weight_kg TO presentation_base_quantity;
ALTER TABLE sale_items RENAME COLUMN weight_kg TO base_quantity;
ALTER TABLE consumption_items RENAME COLUMN presentation_weight_kg TO presentation_base_quantity;
ALTER TABLE consumption_items RENAME COLUMN weight_kg TO base_quantity;

-- COM-29: a purchase line by package; quantity is then the packs and unit_cost the cost of one package
ALTER TABLE purchase_items
    ADD COLUMN presentation_id UUID REFERENCES product_presentations(id),
    ADD COLUMN presentation_name VARCHAR(50),
    ADD COLUMN presentation_base_quantity NUMERIC(12, 3),
    ADD COLUMN base_quantity NUMERIC(12, 3),
    ADD CONSTRAINT chk_purchase_items_presentation CHECK (
        (presentation_id IS NULL AND presentation_name IS NULL AND presentation_base_quantity IS NULL
            AND base_quantity IS NULL)
        OR (presentation_id IS NOT NULL AND presentation_name IS NOT NULL
            AND presentation_base_quantity > 0 AND base_quantity > 0));

-- CAT-15: the factory barcode of a package; the active barcodes stay unique across products and packages
ALTER TABLE product_barcodes ADD COLUMN presentation_id UUID REFERENCES product_presentations(id);
CREATE INDEX idx_product_barcodes_presentation ON product_barcodes(presentation_id) WHERE presentation_id IS NOT NULL;

-- COM-10: a box sold as its own product in the V1, reviewed once: converted into a package of its base product, or
-- discarded (it is not a box)
CREATE TABLE box_product_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    box_product_id UUID NOT NULL UNIQUE REFERENCES products(id),
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('CONVERTED', 'DISCARDED')),
    base_product_id UUID REFERENCES products(id),
    presentation_id UUID REFERENCES product_presentations(id),
    decided_by VARCHAR(100) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_box_product_reviews_conversion CHECK (
        (decision = 'CONVERTED' AND base_product_id IS NOT NULL AND presentation_id IS NOT NULL)
        OR (decision = 'DISCARDED' AND presentation_id IS NULL))
);
