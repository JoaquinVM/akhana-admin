-- T-11 (P-03, CAT-08): products sold by weight need decimal quantities (100 g = 0.100 kg, 1/2 libra).
-- Up to 3 decimals; existing integer quantities convert without loss.

ALTER TABLE sale_items ALTER COLUMN quantity TYPE NUMERIC(12, 3) USING quantity::NUMERIC(12, 3);
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_quantity CHECK (quantity > 0);
