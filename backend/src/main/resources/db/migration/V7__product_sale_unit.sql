-- CAT-08 / POS-10: products sold by weight. The sell price of a KILOGRAM product is Bs per kg,
-- so the POS presets are 100 g = 0.100 and 1/2 libra = 0.227. Existing products are sold by unit.

ALTER TABLE products ADD COLUMN sale_unit VARCHAR(20) NOT NULL DEFAULT 'UNIT';
ALTER TABLE products ADD CONSTRAINT chk_products_sale_unit CHECK (sale_unit IN ('UNIT', 'KILOGRAM'));
